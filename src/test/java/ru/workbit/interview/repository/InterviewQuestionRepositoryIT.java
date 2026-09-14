package ru.workbit.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.vacancy.model.VacancySnapshot;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InterviewQuestionRepositoryIT")
class InterviewQuestionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private InterviewQuestionRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private VacancySnapshot aVacancySnapshot(String name) {
        return VacancySnapshot.builder()
                .name(name)
                .description("Описание вакансии")
                .build();
    }

    private InterviewSession aSession(UUID userId, UUID vacancySnapshotId,
                                       InterviewSession.Status status, Instant created) {
        var builder = InterviewSession.builder()
                .userId(userId)
                .vacancySnapshotId(vacancySnapshotId)
                .status(status)
                .totalQuestions(5)
                .created(created);
        if (status == InterviewSession.Status.COMPLETED) {
            builder.completedAt(created.plusSeconds(600));
        }
        return builder.build();
    }

    private InterviewQuestion aMainQuestion(InterviewSession session, int orderIndex, String text) {
        return InterviewQuestion.builder()
                .session(session)
                .text(text)
                .orderIndex(orderIndex)
                .kind(InterviewQuestion.Kind.MAIN)
                .build();
    }

    private InterviewQuestion aFollowUp(InterviewSession session, int orderIndex, UUID parentQuestionId,
                                         InterviewQuestion.Kind kind, String text) {
        return InterviewQuestion.builder()
                .session(session)
                .text(text)
                .orderIndex(orderIndex)
                .kind(kind)
                .parentQuestionId(parentQuestionId)
                .followUp(true)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindQuestionTexts")
    class FindQuestionTexts {

        @Test
        @DisplayName("Возвращает тексты MAIN-вопросов завершённых сессий, упорядоченные по created сессии и order_index")
        void returnsMainQuestionTextsOrderedBySessionCreatedThenOrderIndex() {
            // given
            var user = em.persistAndFlush(aUser("asked-before@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot("Java-разработчик"));
            var now = Instant.now();

            var olderSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(7200)));
            var q1 = em.persistAndFlush(aMainQuestion(olderSession, 1, "Расскажите про JVM"));
            em.persistAndFlush(aMainQuestion(olderSession, 2, "Что такое GC"));
            em.persistAndFlush(aFollowUp(olderSession, 3, q1.getId(), InterviewQuestion.Kind.FOLLOW_UP,
                    "Уточните, как работает GC"));
            em.persistAndFlush(aFollowUp(olderSession, 4, q1.getId(), InterviewQuestion.Kind.CLARIFICATION,
                    "Поясните термин подробнее"));
            em.persistAndFlush(aFollowUp(olderSession, 5, q1.getId(), InterviewQuestion.Kind.REDIRECT,
                    "Перейдём к другой теме"));

            var newerSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(3600)));
            em.persistAndFlush(aMainQuestion(newerSession, 1, "Что такое Spring"));
            em.persistAndFlush(aMainQuestion(newerSession, 2, "Что такое REST"));

            // when
            var result = repository.findQuestionTexts(user.getId(), List.of(snapshot.getId()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).containsExactly(
                    "Расскажите про JVM", "Что такое GC", "Что такое Spring", "Что такое REST");
        }

        @Test
        @DisplayName("Не учитывает вопросы из сессий в статусе CREATED и IN_PROGRESS")
        void excludesQuestionsFromNonCompletedSessions() {
            // given
            var user = em.persistAndFlush(aUser("not-completed@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot("Backend-разработчик"));
            var now = Instant.now();

            var createdSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId(),
                    InterviewSession.Status.CREATED, now.minusSeconds(120)));
            em.persistAndFlush(aMainQuestion(createdSession, 1, "Вопрос из CREATED"));

            var inProgressSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId(),
                    InterviewSession.Status.IN_PROGRESS, now.minusSeconds(60)));
            em.persistAndFlush(aMainQuestion(inProgressSession, 1, "Вопрос из IN_PROGRESS"));

            // when
            var result = repository.findQuestionTexts(user.getId(), List.of(snapshot.getId()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Не учитывает вопросы другого пользователя по той же вакансии")
        void excludesOtherUsersQuestions() {
            // given
            var user = em.persistAndFlush(aUser("owner-asked@example.com"));
            var stranger = em.persistAndFlush(aUser("stranger-asked@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot("QA-инженер"));
            var now = Instant.now();

            var ownSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(600)));
            em.persistAndFlush(aMainQuestion(ownSession, 1, "Свой вопрос"));

            var strangerSession = em.persistAndFlush(aSession(stranger.getId(), snapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(600)));
            em.persistAndFlush(aMainQuestion(strangerSession, 1, "Чужой вопрос"));

            // when
            var result = repository.findQuestionTexts(user.getId(), List.of(snapshot.getId()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).containsExactly("Свой вопрос");
        }

        @Test
        @DisplayName("Не учитывает вопросы по вакансии, снапшот которой не передан в списке")
        void excludesQuestionsFromVacancySnapshotsNotInList() {
            // given
            var user = em.persistAndFlush(aUser("multi-vacancy@example.com"));
            var targetSnapshot = em.persistAndFlush(aVacancySnapshot("Frontend-разработчик"));
            var otherSnapshot = em.persistAndFlush(aVacancySnapshot("Data Scientist"));
            var now = Instant.now();

            var targetSession = em.persistAndFlush(aSession(user.getId(), targetSnapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(600)));
            em.persistAndFlush(aMainQuestion(targetSession, 1, "Вопрос по нужной вакансии"));

            var otherSession = em.persistAndFlush(aSession(user.getId(), otherSnapshot.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(600)));
            em.persistAndFlush(aMainQuestion(otherSession, 1, "Вопрос по другой вакансии"));

            // when
            var result = repository.findQuestionTexts(user.getId(), List.of(targetSnapshot.getId()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).containsExactly("Вопрос по нужной вакансии");
        }

        @Test
        @DisplayName("Учитывает вопросы из всех переданных снапшотов вакансии")
        void includesQuestionsFromAllPassedVacancySnapshots() {
            // given
            var user = em.persistAndFlush(aUser("several-snapshots@example.com"));
            var snapshotOne = em.persistAndFlush(aVacancySnapshot("Java-разработчик v1"));
            var snapshotTwo = em.persistAndFlush(aVacancySnapshot("Java-разработчик v2"));
            var now = Instant.now();

            var sessionOne = em.persistAndFlush(aSession(user.getId(), snapshotOne.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(7200)));
            em.persistAndFlush(aMainQuestion(sessionOne, 1, "Вопрос по первому снапшоту"));

            var sessionTwo = em.persistAndFlush(aSession(user.getId(), snapshotTwo.getId(),
                    InterviewSession.Status.COMPLETED, now.minusSeconds(3600)));
            em.persistAndFlush(aMainQuestion(sessionTwo, 1, "Вопрос по второму снапшоту"));

            // when
            var result = repository.findQuestionTexts(user.getId(),
                    List.of(snapshotOne.getId(), snapshotTwo.getId()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).containsExactly("Вопрос по первому снапшоту", "Вопрос по второму снапшоту");
        }

        @Test
        @DisplayName("Возвращает пустой список, когда подходящих вопросов нет")
        void returnsEmptyWhenNoMatchingQuestions() {
            // when
            var result = repository.findQuestionTexts(UUID.randomUUID(), List.of(UUID.randomUUID()),
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

            // then
            assertThat(result).isEmpty();
        }
    }
}
