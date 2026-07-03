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
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QuestionRepositoryIT")
class QuestionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private QuestionRepository repository;

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

    private InterviewSession aSession() {
        var userId = aUser("user-" + UUID.randomUUID() + "@example.com");
        var session = InterviewSession.builder()
                .userId(userId)
                .profession(Profession.JAVA_DEV)
                .companyType(CompanyType.STARTUP)
                .level(Level.MIDDLE)
                .status(SessionStatus.CREATED)
                .totalQuestions(5)
                .build();
        return em.persistAndFlush(session);
    }

    private InterviewQuestion unanswered(InterviewSession session, int orderIndex) {
        var question = InterviewQuestion.builder()
                .session(session)
                .category(Category.JAVA_CORE)
                .questionText("Что такое JVM?")
                .orderIndex(orderIndex)
                .build();
        return em.persistAndFlush(question);
    }

    private InterviewQuestion answered(InterviewSession session, int orderIndex) {
        var question = InterviewQuestion.builder()
                .session(session)
                .category(Category.JAVA_CORE)
                .questionText("Что такое JVM?")
                .orderIndex(orderIndex)
                .answered(true)
                .answerText("Виртуальная машина Java")
                .answeredAt(Instant.now())
                .build();
        return em.persistAndFlush(question);
    }

    // =========================================================================

    @Nested
    @DisplayName("CountAnsweredBySessionIds")
    class CountAnsweredBySessionIds {

        @Test
        @DisplayName("Считает только отвеченные вопросы, сгруппированные по сессии")
        void countsOnlyAnsweredQuestionsGroupedBySession() {
            // given
            var sessionA = aSession();
            answered(sessionA, 1);
            unanswered(sessionA, 2);
            var sessionB = aSession();
            unanswered(sessionB, 1);
            var sessionC = aSession();
            answered(sessionC, 1);

            // when
            List<QuestionRepository.AnsweredCount> result = repository.countAnsweredBySessionIds(
                    List.of(sessionA.getId(), sessionB.getId(), sessionC.getId()));

            // then — sessionB не отвечал ни на один вопрос → не попадает в GROUP BY
            assertThat(result)
                    .extracting(QuestionRepository.AnsweredCount::getSessionId, QuestionRepository.AnsweredCount::getCount)
                    .containsExactlyInAnyOrder(
                            tuple(sessionA.getId(), 1L),
                            tuple(sessionC.getId(), 1L));
        }

        @Test
        @DisplayName("Возвращает пустой список, когда ни одна сессия не имеет отвеченных вопросов")
        void returnsEmptyWhenNoAnsweredQuestions() {
            // given
            var session = aSession();
            unanswered(session, 1);

            // when
            List<QuestionRepository.AnsweredCount> result = repository.countAnsweredBySessionIds(List.of(session.getId()));

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CountBySessionIdAndAnsweredTrue")
    class CountBySessionIdAndAnsweredTrue {

        @Test
        @DisplayName("Возвращает количество отвеченных вопросов сессии")
        void returnsCountOfAnsweredQuestions() {
            // given
            var session = aSession();
            answered(session, 1);
            answered(session, 2);
            unanswered(session, 3);

            // when
            long count = repository.countBySessionIdAndAnsweredTrue(session.getId());

            // then
            assertThat(count).isEqualTo(2L);
        }

        @Test
        @DisplayName("Возвращает 0, когда отвеченных вопросов нет")
        void returnsZeroWhenNoAnsweredQuestions() {
            // given
            var session = aSession();
            unanswered(session, 1);

            // when
            long count = repository.countBySessionIdAndAnsweredTrue(session.getId());

            // then
            assertThat(count).isZero();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindNextUnanswered")
    class FindNextUnanswered {

        @Test
        @DisplayName("Возвращает неотвеченный вопрос с минимальным orderIndex")
        void returnsUnansweredQuestionWithMinOrderIndex() {
            // given
            var session = aSession();
            answered(session, 1);
            var expected = unanswered(session, 2);
            unanswered(session, 3);

            // when
            Optional<InterviewQuestion> result = repository.findNextUnanswered(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(expected.getId());
            assertThat(result.get().getOrderIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда все вопросы отвечены")
        void returnsEmptyWhenAllQuestionsAnswered() {
            // given
            var session = aSession();
            answered(session, 1);
            answered(session, 2);

            // when
            Optional<InterviewQuestion> result = repository.findNextUnanswered(session.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindBySessionIdAndOrderIndex")
    class FindBySessionIdAndOrderIndex {

        @Test
        @DisplayName("Возвращает вопрос, когда он существует у сессии")
        void returnsQuestionWhenExists() {
            // given
            var session = aSession();
            var question = unanswered(session, 1);

            // when
            Optional<InterviewQuestion> result = repository.findBySessionIdAndOrderIndex(session.getId(), 1);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(question.getId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда вопроса с таким orderIndex нет")
        void returnsEmptyWhenOrderIndexNotFound() {
            // given
            var session = aSession();
            unanswered(session, 1);

            // when
            Optional<InterviewQuestion> result = repository.findBySessionIdAndOrderIndex(session.getId(), 2);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindWithSessionById")
    class FindWithSessionById {

        @Test
        @DisplayName("Подгружает сессию вопроса через JOIN FETCH")
        void fetchesSession() {
            // given
            var session = aSession();
            var question = unanswered(session, 1);
            em.clear();

            // when
            Optional<InterviewQuestion> result = repository.findWithSessionById(question.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getSession().getId()).isEqualTo(session.getId());
            assertThat(result.get().getSession().getUserId()).isEqualTo(session.getUserId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда вопрос не найден")
        void returnsEmptyWhenQuestionNotFound() {
            // when
            Optional<InterviewQuestion> result = repository.findWithSessionById(UUID.randomUUID());

            // then
            assertThat(result).isEmpty();
        }
    }
}
