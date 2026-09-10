package ru.workbit.training.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
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
import ru.workbit.training.model.TrainingFeedback;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TrainingSessionRepositoryIT")
class TrainingSessionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private TrainingSessionRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private TrainingSession aCompletedSession(UUID userId) {
        return TrainingSession.builder()
                .userId(userId)
                .skill("Spring Core")
                .profession("Java-разработчик")
                .level(TrainingSession.Level.EASY)
                .status(TrainingSession.Status.COMPLETED)
                .completedAt(Instant.now())
                .build();
    }

    private TrainingQuestion anAnsweredQuestion(TrainingSession session, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .answered(true)
                .answerText("Ответ " + orderIndex)
                .answeredAt(Instant.now())
                .build();
    }

    private TrainingFeedback aFeedback(TrainingQuestion question) {
        return TrainingFeedback.builder()
                .question(question)
                .score(4)
                .text("Ответ по делу")
                .build();
    }

    private TrainingReport aReport(TrainingSession session) {
        return TrainingReport.builder()
                .trainingSession(session)
                .avgScore(4.0)
                .overallFeedback("Хороший результат")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("Restart")
    class Restart {

        @Test
        @DisplayName("Обнуление отчёта и фидбэка стирает их, но оставляет сессию и вопросы")
        void clearingReportAndFeedbackKeepsSessionAndQuestions() {
            // given
            var user = em.persistAndFlush(aUser("restart-keeps-session@example.com"));
            var session = em.persistAndFlush(aCompletedSession(user.getId()));
            var question = em.persistAndFlush(anAnsweredQuestion(session, 1));
            var feedback = em.persistAndFlush(aFeedback(question));
            var report = em.persistAndFlush(aReport(session));
            em.clear();

            // when
            var loaded = repository.findWithQuestionsById(session.getId()).orElseThrow();
            for (TrainingQuestion loadedQuestion : loaded.getQuestions()) {
                loadedQuestion.setFeedback(null);
                loadedQuestion.setAnswered(false);
                loadedQuestion.setAnswerText(null);
                loadedQuestion.setAnsweredAt(null);
            }
            loaded.setReport(null);
            loaded.setStatus(TrainingSession.Status.CREATED);
            loaded.setCompletedAt(null);

            // then
            assertThatCode(em::flush).doesNotThrowAnyException();

            em.clear();
            assertThat(em.find(TrainingFeedback.class, feedback.getId())).isNull();
            assertThat(em.find(TrainingReport.class, report.getId())).isNull();
            assertThat(em.find(TrainingQuestion.class, question.getId())).isNotNull();
            assertThat(em.find(TrainingSession.class, session.getId())).isNotNull();
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Удаление сессии уносит вопросы, фидбэк и отчёт")
        void deletingSessionRemovesQuestionsFeedbackAndReport() {
            // given
            var user = em.persistAndFlush(aUser("delete-cascades@example.com"));
            var session = em.persistAndFlush(aCompletedSession(user.getId()));
            var question = em.persistAndFlush(anAnsweredQuestion(session, 1));
            var feedback = em.persistAndFlush(aFeedback(question));
            var report = em.persistAndFlush(aReport(session));
            em.clear();

            // when
            repository.deleteById(session.getId());
            em.flush();
            em.clear();

            // then
            assertThat(em.find(TrainingSession.class, session.getId())).isNull();
            assertThat(em.find(TrainingQuestion.class, question.getId())).isNull();
            assertThat(em.find(TrainingFeedback.class, feedback.getId())).isNull();
            assertThat(em.find(TrainingReport.class, report.getId())).isNull();
        }
    }
}
