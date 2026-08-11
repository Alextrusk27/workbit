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
import ru.workbit.interview.model.InterviewFeedback;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.vacancy.model.VacancySnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InterviewSessionRepositoryIT")
class InterviewSessionRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private InterviewSessionRepository repository;

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
        return aSession(userId, vacancySnapshotId, Instant.now());
    }

    private InterviewSession aSession(UUID userId, UUID vacancySnapshotId, Instant created) {
        return InterviewSession.builder()
                .userId(userId)
                .vacancySnapshotId(vacancySnapshotId)
                .totalQuestions(5)
                .created(created)
                .build();
    }

    private InterviewQuestion aQuestion(InterviewSession session, int orderIndex) {
        return InterviewQuestion.builder()
                .session(session)
                .text("Вопрос " + orderIndex)
                .orderIndex(orderIndex)
                .build();
    }

    private InterviewFeedback aFeedback(InterviewQuestion question, int score) {
        return InterviewFeedback.builder()
                .question(question)
                .score(score)
                .text("Фидбэк")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserIdOrderByCreatedDesc")
    class FindAllByUserIdOrderByCreatedDesc {

        @Test
        @DisplayName("Возвращает сессии пользователя по убыванию created — новые первыми")
        void returnsSessionsOrderedByCreatedDescending() {
            // given — вставляем в перемешанном порядке (не по created), с разными значениями created
            var user = em.persistAndFlush(aUser("order-desc@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var now = Instant.now();

            var middle = em.persistAndFlush(aSession(user.getId(), snapshot.getId(), now.minusSeconds(3600)));
            var oldest = em.persistAndFlush(aSession(user.getId(), snapshot.getId(), now.minusSeconds(7200)));
            var newest = em.persistAndFlush(aSession(user.getId(), snapshot.getId(), now));

            // when
            List<InterviewSession> result = repository.findAllByUserIdOrderByCreatedDesc(user.getId());

            // then
            assertThat(result).extracting(InterviewSession::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        @DisplayName("Не возвращает сессии другого пользователя")
        void excludesOtherUsersSessions() {
            // given
            var user = em.persistAndFlush(aUser("owner@example.com"));
            var otherUser = em.persistAndFlush(aUser("other@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var ownSession = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            em.persistAndFlush(aSession(otherUser.getId(), snapshot.getId()));

            // when
            List<InterviewSession> result = repository.findAllByUserIdOrderByCreatedDesc(user.getId());

            // then
            assertThat(result).extracting(InterviewSession::getId)
                    .containsExactly(ownSession.getId());
        }

        @Test
        @DisplayName("Возвращает пустой список, когда у пользователя нет сессий")
        void returnsEmptyListWhenUserHasNoSessions() {
            // when
            List<InterviewSession> result = repository.findAllByUserIdOrderByCreatedDesc(UUID.randomUUID());

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
        void returnsSessionWhenIdAndUserIdMatch() {
            // given
            var user = em.persistAndFlush(aUser("find-owner@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));

            // when
            var result = repository.findByIdAndUserId(session.getId(), user.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("Возвращает empty, когда сессия принадлежит другому пользователю")
        void returnsEmptyWhenSessionBelongsToAnotherUser() {
            // given
            var owner = em.persistAndFlush(aUser("find-real-owner@example.com"));
            var stranger = em.persistAndFlush(aUser("find-stranger@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(owner.getId(), snapshot.getId()));

            // when / then
            assertThat(repository.findByIdAndUserId(session.getId(), stranger.getId())).isEmpty();
        }

        @Test
        @DisplayName("Возвращает empty, когда сессия с таким id не найдена")
        void returnsEmptyWhenSessionNotFound() {
            // when / then
            assertThat(repository.findByIdAndUserId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindWithQuestionsById")
    class FindWithQuestionsById {

        @Test
        @DisplayName("Загружает сессию с вопросами и их фидбэком через JOIN FETCH")
        void loadsSessionWithQuestionsAndFeedback() {
            // given
            var user = em.persistAndFlush(aUser("with-questions@example.com"));
            var snapshot = em.persistAndFlush(aVacancySnapshot());
            var session = em.persistAndFlush(aSession(user.getId(), snapshot.getId()));
            var question = em.persistAndFlush(aQuestion(session, 1));
            em.persistAndFlush(aFeedback(question, 4));
            em.clear();

            // when
            var result = repository.findWithQuestionsById(session.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getQuestions()).hasSize(1);
            assertThat(result.get().getQuestions().get(0).getFeedback().getScore()).isEqualTo(4);
        }

        @Test
        @DisplayName("Возвращает empty, когда сессия с таким id не найдена")
        void returnsEmptyWhenSessionNotFound() {
            // when / then
            assertThat(repository.findWithQuestionsById(UUID.randomUUID())).isEmpty();
        }
    }

}
