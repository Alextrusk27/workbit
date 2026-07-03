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
import ru.workbit.interview.model.AnswerFeedback;
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.SessionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("FeedbackRepositoryIT")
class FeedbackRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private FeedbackRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private UUID aUser(String email) {
        var user = User.builder()
                .email(email)
                .password("hashed_password")
                .build();
        return em.persistAndFlush(user).getId();
    }

    private InterviewQuestion aQuestion() {
        var userId = aUser("user-" + UUID.randomUUID() + "@example.com");
        var session = InterviewSession.builder()
                .userId(userId)
                .profession(Profession.JAVA_DEV)
                .companyType(CompanyType.STARTUP)
                .level(Level.MIDDLE)
                .status(SessionStatus.CREATED)
                .totalQuestions(5)
                .build();
        em.persistAndFlush(session);
        var question = InterviewQuestion.builder()
                .session(session)
                .category(Category.JAVA_CORE)
                .questionText("Что такое JVM?")
                .orderIndex(1)
                .build();
        return em.persistAndFlush(question);
    }

    private AnswerFeedback aFeedback(InterviewQuestion question) {
        return AnswerFeedback.builder()
                .question(question)
                .score(4)
                .feedbackText("Хороший ответ")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("UUID генерируется автоматически при сохранении")
        void uuidIsGeneratedOnSave() {
            // given
            var feedback = aFeedback(aQuestion());
            assertThat(feedback.getId()).isNull();

            // when
            var saved = em.persistFlushFind(feedback);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Round-trip: фидбек читается из БД со связанным вопросом и всеми полями")
        void roundTripLinksToQuestion() {
            // given
            var question = aQuestion();
            var feedback = aFeedback(question);

            // when
            var saved = repository.saveAndFlush(feedback);
            em.clear();
            Optional<AnswerFeedback> result = repository.findById(saved.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getQuestion().getId()).isEqualTo(question.getId());
            assertThat(result.get().getScore()).isEqualTo(4);
            assertThat(result.get().getFeedbackText()).isEqualTo("Хороший ответ");
            assertThat(result.get().getGeneratedAt()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueQuestion")
    class UniqueQuestion {

        @Test
        @DisplayName("Второй фидбек на тот же вопрос нарушает UNIQUE-констрейнт при flush")
        void throwsOnDuplicateFeedbackForSameQuestion() {
            // given
            var question = aQuestion();
            em.persistAndFlush(aFeedback(question));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aFeedback(question)))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintScore")
    class CheckConstraintScore {

        @Test
        @DisplayName("Score вне диапазона 1..5 нарушает CHECK-констрейнт при flush")
        void throwsWhenScoreOutOfRange() {
            // given
            var bad = aFeedback(aQuestion());
            bad.setScore(6);

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Score в диапазоне 1..5 сохраняется без ошибок")
        void savesSuccessfullyWhenScoreInRange() {
            // given
            var feedback = aFeedback(aQuestion());
            feedback.setScore(1);

            // when
            var saved = em.persistFlushFind(feedback);

            // then
            assertThat(saved.getScore()).isEqualTo(1);
        }
    }
}
