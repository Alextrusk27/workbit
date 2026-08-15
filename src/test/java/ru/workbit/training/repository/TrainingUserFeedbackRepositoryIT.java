package ru.workbit.training.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.TrainingUserFeedback;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TrainingUserFeedbackRepositoryIT")
class TrainingUserFeedbackRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private TrainingUserFeedbackRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private TrainingSession aSession(UUID userId) {
        return TrainingSession.builder()
                .userId(userId)
                .skill("Spring Core")
                .profession("Java-разработчик")
                .level(TrainingSession.Level.JUNIOR)
                .build();
    }

    private TrainingQuestion aQuestion(TrainingSession session, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    private TrainingUserFeedback aFeedback(UUID sessionId, UUID questionId, List<String> reasons) {
        return TrainingUserFeedback.builder()
                .sessionId(sessionId)
                .questionId(questionId)
                .vote(TrainingUserFeedback.Vote.DOWN)
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
            var user = em.persistAndFlush(aUser("feedback-save@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1));
            var feedback = aFeedback(session.getId(), question.getId(),
                    List.of("too_generic", "wrong_grade"));

            // when
            var saved = em.persistFlushFind(feedback);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getSessionId()).isEqualTo(session.getId());
            assertThat(saved.getQuestionId()).isEqualTo(question.getId());
            assertThat(saved.getVote()).isEqualTo(TrainingUserFeedback.Vote.DOWN);
            assertThat(saved.getReasons()).containsExactly("too_generic", "wrong_grade");
            assertThat(saved.getCreated()).isNotNull();
        }

        @Test
        @DisplayName("Отзыв на отчёт сохраняется с question_id = null")
        void savesReportFeedbackWithNullQuestionId() {
            // given
            var user = em.persistAndFlush(aUser("feedback-report@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
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
            var user = em.persistAndFlush(aUser("feedback-cascade-session@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
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
            var user = em.persistAndFlush(aUser("feedback-cascade-question@example.com"));
            var session = em.persistAndFlush(aSession(user.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1));
            var questionFeedback = em.persistAndFlush(aFeedback(session.getId(), question.getId(), List.of("reason")));
            var reportFeedback = em.persistAndFlush(aFeedback(session.getId(), null, List.of("reason")));

            // when — физическое удаление вопроса нативным SQL, чтобы проверить реальный
            // ON DELETE CASCADE в БД, минуя JPA-кеш
            em.getEntityManager()
                    .createNativeQuery("DELETE FROM training.question WHERE id = :id")
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
