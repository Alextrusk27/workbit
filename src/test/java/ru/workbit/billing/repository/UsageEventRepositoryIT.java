package ru.workbit.billing.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.billing.model.UsageEvent;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UsageEventRepositoryIT")
class UsageEventRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private UsageEventRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private UsageEvent aUsageEvent(UUID userId, Instant at) {
        return UsageEvent.builder()
                .userId(userId)
                .at(at)
                .kind(UsageEvent.Kind.SPEND)
                .target(UsageEvent.Target.INTERVIEW)
                .delta(1)
                .label("Интервью — Java-разработчик")
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserIdOrderByAtDesc")
    class FindAllByUserIdOrderByAtDesc {

        @Test
        @DisplayName("Возвращает события пользователя по убыванию at — новые первыми")
        void returnsEventsOrderedByAtDescending() {
            // given — вставляем в перемешанном порядке (не по at)
            var user = em.persistAndFlush(aUser("usage-order-desc@example.com"));
            var now = Instant.now();
            var middle = em.persistAndFlush(aUsageEvent(user.getId(), now.minusSeconds(3600)));
            var oldest = em.persistAndFlush(aUsageEvent(user.getId(), now.minusSeconds(7200)));
            var newest = em.persistAndFlush(aUsageEvent(user.getId(), now));

            // when
            var result = repository.findAllByUserIdOrderByAtDesc(user.getId());

            // then
            assertThat(result).extracting(UsageEvent::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        @DisplayName("Не возвращает события другого пользователя")
        void excludesOtherUsersEvents() {
            // given
            var user = em.persistAndFlush(aUser("usage-owner@example.com"));
            var otherUser = em.persistAndFlush(aUser("usage-other@example.com"));
            var ownEvent = em.persistAndFlush(aUsageEvent(user.getId(), Instant.now()));
            em.persistAndFlush(aUsageEvent(otherUser.getId(), Instant.now()));

            // when
            var result = repository.findAllByUserIdOrderByAtDesc(user.getId());

            // then
            assertThat(result).extracting(UsageEvent::getId).containsExactly(ownEvent.getId());
        }

        @Test
        @DisplayName("Возвращает пустой список, когда у пользователя нет событий")
        void returnsEmptyListWhenUserHasNoEvents() {
            // when / then
            assertThat(repository.findAllByUserIdOrderByAtDesc(UUID.randomUUID())).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("ON DELETE CASCADE: удаление пользователя из auth.users удаляет его usage_event")
        void cascadeDeleteRemovesEventsOnUserDelete() {
            // given
            var user = em.persistAndFlush(aUser("usage-cascade-del@example.com"));
            var userId = user.getId();
            em.persistAndFlush(aUsageEvent(userId, Instant.now()));

            // when — физическое удаление пользователя через managed-ссылку.
            // em.clear() перед remove: иначе managed UsageEvent в контексте персистентности
            // при flush может выбросить TransientPropertyValueException.
            em.flush();
            em.clear();
            var managed = requireNonNull(em.find(User.class, userId));
            em.remove(managed);
            em.flush();
            em.clear();

            // then
            assertThat(repository.findAllByUserIdOrderByAtDesc(userId)).isEmpty();
        }
    }
}
