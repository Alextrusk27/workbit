package ru.workbit.auth.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserJPARepositoryIT")
class UserJPARepositoryIT extends AbstractPostgresIT {

    @Autowired
    private UserJPARepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build(); // emailVerified=false, created=now() — @Builder.Default
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByEmail")
    class FindByEmail {

        @Test
        @DisplayName("Возвращает пользователя, когда email существует")
        void returnsUserWhenEmailExists() {
            // given
            var saved = em.persistAndFlush(aUser("find@example.com"));

            // when
            Optional<User> result = repository.findByEmail("find@example.com");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
            assertThat(result.get().getEmail()).isEqualTo("find@example.com");
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда email не найден")
        void returnsEmptyWhenEmailNotFound() {
            // when
            Optional<User> result = repository.findByEmail("nobody@example.com");

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueEmail")
    class UniqueEmail {

        @Test
        @DisplayName("Дублирующий email бросает исключение при flush")
        void throwsOnDuplicateEmail() {
            // given
            em.persistAndFlush(aUser("dup@example.com"));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(aUser("dup@example.com")))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("UUID генерируется автоматически при сохранении")
        void uuidIsGeneratedOnSave() {
            // given
            var user = aUser("uuid-gen@example.com");
            assertThat(user.getId()).isNull();

            // when
            var saved = em.persistFlushFind(user);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Round-trip всех полей: данные читаются из БД идентично сохранённым")
        void roundTripAllFields() {
            // given
            var now = Instant.now();
            var user = User.builder()
                    .email("roundtrip@example.com")
                    .emailVerified(true)
                    .created(now)
                    .build();

            // when
            var saved = em.persistFlushFind(user);

            // then
            assertThat(saved.getEmail()).isEqualTo("roundtrip@example.com");
            assertThat(saved.isEmailVerified()).isTrue();
            assertThat(saved.getCreated()).isNotNull();
        }

        @Test
        @DisplayName("Дефолт emailVerified=false применяется из @Builder.Default")
        void defaultEmailVerifiedIsApplied() {
            // given / when
            var saved = em.persistFlushFind(aUser("defaults@example.com"));

            // then
            assertThat(saved.isEmailVerified()).isFalse();
            assertThat(saved.getCreated()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DeleteById")
    class DeleteById {

        @Test
        @DisplayName("Физическое удаление: запись пользователя исчезает из БД")
        void removesUserRecord() {
            // given
            var saved = em.persistAndFlush(aUser("delete-me@example.com"));

            // when
            repository.deleteById(saved.getId());
            em.flush();

            // then
            assertThat(repository.findByEmail("delete-me@example.com")).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByLastSeenBeforeAndDeletionWarnedAtIsNull")
    class FindByLastSeenBeforeAndDeletionWarnedAtIsNull {

        private final Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);

        @Test
        @DisplayName("Возвращает неактивного пользователя без отметки о предупреждении")
        void returnsInactiveUserWithoutWarning() {
            // given
            var inactive = User.builder()
                    .email("inactive@example.com")
                    .lastSeen(threshold.minus(1, ChronoUnit.DAYS))
                    .build();
            em.persistAndFlush(inactive);

            // when
            List<User> result = repository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(threshold);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("inactive@example.com");
        }

        @Test
        @DisplayName("Не возвращает активного пользователя со свежим lastSeen")
        void excludesActiveUser() {
            // given
            var active = User.builder()
                    .email("active@example.com")
                    .lastSeen(Instant.now())
                    .build();
            em.persistAndFlush(active);

            // when
            List<User> result = repository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(threshold);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Не возвращает уже предупреждённого пользователя, даже с устаревшим lastSeen")
        void excludesAlreadyWarnedUser() {
            // given
            var warned = User.builder()
                    .email("warned@example.com")
                    .lastSeen(threshold.minus(10, ChronoUnit.DAYS))
                    .deletionWarnedAt(Instant.now().minus(5, ChronoUnit.DAYS))
                    .build();
            em.persistAndFlush(warned);

            // when
            List<User> result = repository.findByLastSeenBeforeAndDeletionWarnedAtIsNull(threshold);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DeleteByDeletionWarnedAtBefore")
    class DeleteByDeletionWarnedAtBefore {

        private final Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);

        @Test
        @DisplayName("Удаляет пользователей с deletionWarnedAt старше порога и возвращает корректный count")
        void deletesUsersWarnedBeforeThresholdAndReturnsCount() {
            // given
            var old1 = User.builder()
                    .email("old-warned-1@example.com")
                    .deletionWarnedAt(threshold.minus(1, ChronoUnit.DAYS))
                    .build();
            var old2 = User.builder()
                    .email("old-warned-2@example.com")
                    .deletionWarnedAt(threshold.minus(2, ChronoUnit.DAYS))
                    .build();
            em.persistAndFlush(old1);
            em.persistAndFlush(old2);

            // when
            int deleted = repository.deleteByDeletionWarnedAtBefore(threshold);
            em.flush();
            em.clear();

            // then
            assertThat(deleted).isEqualTo(2);
            assertThat(repository.findByEmail("old-warned-1@example.com")).isEmpty();
            assertThat(repository.findByEmail("old-warned-2@example.com")).isEmpty();
        }

        @Test
        @DisplayName("Не удаляет пользователя с deletionWarnedAt = null")
        void keepsUserWithNullWarnedAt() {
            // given
            var noWarning = User.builder()
                    .email("no-warning@example.com")
                    .build();
            em.persistAndFlush(noWarning);

            // when
            int deleted = repository.deleteByDeletionWarnedAtBefore(threshold);
            em.flush();
            em.clear();

            // then
            assertThat(deleted).isZero();
            assertThat(repository.findByEmail("no-warning@example.com")).isPresent();
        }

        @Test
        @DisplayName("Не удаляет пользователя со свежим deletionWarnedAt")
        void keepsUserWithFreshWarnedAt() {
            // given
            var freshWarning = User.builder()
                    .email("fresh-warning@example.com")
                    .deletionWarnedAt(Instant.now())
                    .build();
            em.persistAndFlush(freshWarning);

            // when
            int deleted = repository.deleteByDeletionWarnedAtBefore(threshold);
            em.flush();
            em.clear();

            // then
            assertThat(deleted).isZero();
            assertThat(repository.findByEmail("fresh-warning@example.com")).isPresent();
        }
    }
}
