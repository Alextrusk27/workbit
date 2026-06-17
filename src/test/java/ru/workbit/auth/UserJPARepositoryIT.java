package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.user.model.User;
import ru.workbit.user.repository.UserJPARepository;

import java.time.Instant;
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
                .password("hashed_password")
                .build(); // active=true, deactivated=null, emailVerified=false — @Builder.Default
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
    @DisplayName("CheckConstraintActiveDeactivated")
    class CheckConstraintActiveDeactivated {

        @Test
        @DisplayName("active=false и deactivated=null нарушает CHECK-констрейнт при flush")
        void throwsWhenActiveIsFalseAndDeactivatedIsNull() {
            // given — нарушаем инвариант: active=false, deactivated=null
            var bad = User.builder()
                    .email("bad-false@example.com")
                    .password("hashed_password")
                    .active(false)
                    .deactivated(null)
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("active=true и deactivated != null нарушает CHECK-констрейнт при flush")
        void throwsWhenActiveIsTrueAndDeactivatedIsNotNull() {
            // given — нарушаем инвариант: active=true, deactivated заполнен
            var bad = User.builder()
                    .email("bad-true@example.com")
                    .password("hashed_password")
                    .active(true)
                    .deactivated(Instant.now())
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Корректный soft delete (active=false, deactivated != null) сохраняется без ошибок")
        void softDeleteSavesSuccessfully() {
            // given
            var user = em.persistAndFlush(aUser("soft-delete@example.com"));

            // when — деактивируем: соблюдаем инвариант
            user.setActive(false);
            user.setDeactivated(Instant.now());
            em.persistAndFlush(user);
            em.clear();

            // then — читаем из БД и проверяем состояние
            var found = repository.findByEmail("soft-delete@example.com");
            assertThat(found).isPresent();
            assertThat(found.get().isActive()).isFalse();
            assertThat(found.get().getDeactivated()).isNotNull();
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
                    .password("hashed_roundtrip")
                    .emailVerified(true)
                    .active(true)
                    .created(now)
                    .build();

            // when
            var saved = em.persistFlushFind(user);

            // then
            assertThat(saved.getEmail()).isEqualTo("roundtrip@example.com");
            assertThat(saved.getPassword()).isEqualTo("hashed_roundtrip");
            assertThat(saved.isEmailVerified()).isTrue();
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getDeactivated()).isNull();
            assertThat(saved.getCreated()).isNotNull();
        }

        @Test
        @DisplayName("Дефолты emailVerified=false и active=true применяются из @Builder.Default")
        void defaultsAreApplied() {
            // given / when
            var saved = em.persistFlushFind(aUser("defaults@example.com"));

            // then
            assertThat(saved.isEmailVerified()).isFalse();
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getDeactivated()).isNull();
            assertThat(saved.getCreated()).isNotNull();
        }
    }
}
