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
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.AnswerFeedback;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SessionRepositoryIT")
class SessionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private SessionRepository repository;

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

    private InterviewSession aSession(UUID userId) {
        return InterviewSession.builder()
                .userId(userId)
                .profession(Profession.JAVA_DEV)
                .companyType(CompanyType.STARTUP)
                .level(Level.MIDDLE)
                .status(SessionStatus.CREATED)
                .totalQuestions(5)
                .build();
    }

    private InterviewQuestion aQuestion(InterviewSession session, int orderIndex) {
        return InterviewQuestion.builder()
                .session(session)
                .category(Category.JAVA_CORE)
                .questionText("Что такое JVM?")
                .orderIndex(orderIndex)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserId")
    class FindAllByUserId {

        @Test
        @DisplayName("Возвращает только сессии искомого пользователя")
        void returnsOnlySessionsOfGivenUser() {
            // given
            var userId = aUser("owner@example.com");
            var otherUserId = aUser("other@example.com");
            var session1 = em.persistAndFlush(aSession(userId));
            var session2 = em.persistAndFlush(aSession(userId));
            em.persistAndFlush(aSession(otherUserId));

            // when
            List<InterviewSession> result = repository.findAllByUserId(userId);

            // then
            assertThat(result)
                    .extracting(InterviewSession::getId)
                    .containsExactlyInAnyOrder(session1.getId(), session2.getId());
        }

        @Test
        @DisplayName("Возвращает пустой список, когда у пользователя нет сессий")
        void returnsEmptyListWhenNoSessions() {
            // given
            var userId = aUser("no-sessions@example.com");

            // when
            List<InterviewSession> result = repository.findAllByUserId(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByIdAndUserId")
    class FindByIdAndUserId {

        @Test
        @DisplayName("Возвращает сессию, когда id и userId совпадают")
        void returnsSessionWhenOwnedByUser() {
            // given
            var userId = aUser("owner2@example.com");
            var session = em.persistAndFlush(aSession(userId));

            // when
            Optional<InterviewSession> result = repository.findByIdAndUserId(session.getId(), userId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда сессия принадлежит другому пользователю")
        void returnsEmptyWhenOwnedByAnotherUser() {
            // given
            var userId = aUser("owner3@example.com");
            var strangerId = aUser("stranger@example.com");
            var session = em.persistAndFlush(aSession(userId));

            // when
            Optional<InterviewSession> result = repository.findByIdAndUserId(session.getId(), strangerId);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("ExistsByIdAndUserId")
    class ExistsByIdAndUserId {

        @Test
        @DisplayName("Возвращает true, когда сессия принадлежит пользователю")
        void returnsTrueWhenOwnedByUser() {
            // given
            var userId = aUser("owner4@example.com");
            var session = em.persistAndFlush(aSession(userId));

            // when
            boolean exists = repository.existsByIdAndUserId(session.getId(), userId);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Возвращает false, когда сессия принадлежит другому пользователю")
        void returnsFalseWhenOwnedByAnotherUser() {
            // given
            var userId = aUser("owner5@example.com");
            var strangerId = aUser("stranger2@example.com");
            var session = em.persistAndFlush(aSession(userId));

            // when
            boolean exists = repository.existsByIdAndUserId(session.getId(), strangerId);

            // then
            assertThat(exists).isFalse();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindWithQuestionsById")
    class FindWithQuestionsById {

        @Test
        @DisplayName("Подгружает вопросы и фидбек сессии через JOIN FETCH")
        void fetchesQuestionsAndFeedback() {
            // given
            var userId = aUser("owner6@example.com");
            var session = em.persistAndFlush(aSession(userId));
            var question1 = em.persistAndFlush(aQuestion(session, 1));
            var question2 = em.persistAndFlush(aQuestion(session, 2));
            var feedback = AnswerFeedback.builder()
                    .question(question1)
                    .score(4)
                    .feedbackText("Хороший ответ")
                    .build();
            em.persistAndFlush(feedback);
            em.clear();

            // when
            Optional<InterviewSession> result = repository.findWithQuestionsById(session.getId());

            // then
            assertThat(result).isPresent();
            var loaded = result.get();
            assertThat(loaded.getQuestions()).hasSize(2);
            var loadedQuestion1 = loaded.getQuestions().stream()
                    .filter(q -> q.getOrderIndex() == 1)
                    .findFirst().orElseThrow();
            var loadedQuestion2 = loaded.getQuestions().stream()
                    .filter(q -> q.getOrderIndex() == 2)
                    .findFirst().orElseThrow();
            assertThat(loadedQuestion1.getFeedback()).isNotNull();
            assertThat(loadedQuestion1.getFeedback().getScore()).isEqualTo(4);
            assertThat(loadedQuestion2.getFeedback()).isNull();
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда у сессии нет вопросов (JOIN FETCH не находит строк)")
        void returnsEmptyWhenSessionHasNoQuestions() {
            // given
            var userId = aUser("owner7@example.com");
            var session = em.persistAndFlush(aSession(userId));

            // when
            Optional<InterviewSession> result = repository.findWithQuestionsById(session.getId());

            // then — INNER JOIN FETCH по вопросам не даёт строк, если вопросов нет
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CheckConstraintCompletedAt")
    class CheckConstraintCompletedAt {

        @Test
        @DisplayName("Статус COMPLETED без completedAt нарушает CHECK-констрейнт при flush")
        void throwsWhenCompletedWithoutCompletedAt() {
            // given
            var userId = aUser("owner8@example.com");
            var bad = aSession(userId);
            bad.setStatus(SessionStatus.COMPLETED);
            bad.setCompletedAt(null);

            // when / then
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Статус COMPLETED с заполненным completedAt сохраняется без ошибок")
        void savesSuccessfullyWhenCompletedWithCompletedAt() {
            // given
            var userId = aUser("owner9@example.com");
            var session = aSession(userId);
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());

            // when
            var saved = em.persistFlushFind(session);

            // then
            assertThat(saved.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(saved.getCompletedAt()).isNotNull();
        }
    }
}
