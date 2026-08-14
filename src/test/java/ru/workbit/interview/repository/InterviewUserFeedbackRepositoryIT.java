package ru.workbit.interview.repository;

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
import ru.workbit.interview.model.InterviewUserFeedback;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InterviewUserFeedbackRepositoryIT")
class InterviewUserFeedbackRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private InterviewUserFeedbackRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private VacancySnapshot aVacancySnapshot() {
        return VacancySnapshot.builder()
                .name("Java-разработчик")
                .description("Описание вакансии")
                .build();
    }

    private InterviewSession aSession(UUID userId, UUID vacancySnapshotId) {
        return InterviewSession.builder()
                .userId(userId)
                .vacancySnapshotId(vacancySnapshotId)
                .totalQuestions(5)
                .build();
    }

    private InterviewQuestion aQuestion(InterviewSession session, int orderIndex) {
        return InterviewQuestion.builder()
                .session(session)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    private InterviewUserFeedback aFeedback(UUID sessionId, UUID questionId, List<String> reasons) {
        return InterviewUserFeedback.builder()
                .sessionId(sessionId)
                .questionId(questionId)
                .vote(InterviewUserFeedback.Vote.DOWN)
                .reasons(reasons)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("Save")
    class Save {

        @Test
        @DisplayName("Сохраняет отзыв на вопрос с непустым reasons и читает его обратно (text[])")
        void savesFeedbackWithReasonsRoundTrip() {
            // given
            var user = em.persistAndFlush(aUser("interview-feedback-save@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1));
            var feedback = aFeedback(session.getId(), question.getId(),
                    List.of("too_generic", "wrong_grade"));

            // when
            var saved = em.persistFlushFind(feedback);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getSessionId()).isEqualTo(session.getId());
            assertThat(saved.getQuestionId()).isEqualTo(question.getId());
            assertThat(saved.getVote()).isEqualTo(InterviewUserFeedback.Vote.DOWN);
            assertThat(saved.getReasons()).containsExactly("too_generic", "wrong_grade");
            assertThat(saved.getCreated()).isNotNull();
        }

        @Test
        @DisplayName("Отзыв на отчёт сохраняется с question_id = null")
        void savesReportFeedbackWithNullQuestionId() {
            // given
            var user = em.persistAndFlush(aUser("interview-feedback-report@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            var feedback = aFeedback(session.getId(), null, List.of("great_report"));

            // when
            var saved = em.persistFlushFind(feedback);

            // then
            assertThat(saved.getQuestionId()).isNull();
            assertThat(saved.getSessionId()).isEqualTo(session.getId());
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("ON DELETE CASCADE: удаление сессии удаляет её отзывы")
        void cascadeDeleteRemovesFeedbackOnSessionDelete() {
            // given
            var user = em.persistAndFlush(aUser("interview-feedback-cascade-session@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            var feedback = em.persistAndFlush(aFeedback(session.getId(), null, List.of("reason")));

            // when
            em.remove(session);
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(feedback.getId())).isEmpty();
        }

        @Test
        @DisplayName("ON DELETE CASCADE: удаление вопроса удаляет отзыв на вопрос, отзыв на отчёт остаётся")
        void cascadeDeleteRemovesOnlyQuestionFeedback() {
            // given
            var user = em.persistAndFlush(aUser("interview-feedback-cascade-question@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1));
            var questionFeedback = em.persistAndFlush(aFeedback(session.getId(), question.getId(), List.of("reason")));
            var reportFeedback = em.persistAndFlush(aFeedback(session.getId(), null, List.of("reason")));

            // when — физическое удаление вопроса нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM interview.question WHERE id = :id")
                    .setParameter("id", question.getId())
                    .executeUpdate();
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(questionFeedback.getId())).isEmpty();
            assertThat(repository.findById(reportFeedback.getId())).isPresent();
        }
    }
}
