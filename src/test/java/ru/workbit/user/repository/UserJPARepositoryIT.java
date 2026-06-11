package ru.workbit.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.user.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserJPARepositoryIT")
class UserJPARepositoryIT extends AbstractPostgresIT {

    @Autowired
    private UserJPARepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрика активного пользователя (доменный инвариант: active=true, deactivated=null) ---
    private User aUser(String email) {
        return User.builder()
                .email(email)
                .lastName("Иванов")
                .firstName("Иван")
                .middleName("Иванович")
                .password("hashed_password")
                .build(); // active=true по @Builder.Default, deactivated=null
    }

    // --- фабрика деактивированного пользователя (active=false, deactivated != null) ---
    private User aDeactivatedUser(String email) {
        return User.builder()
                .email(email)
                .lastName("Петров")
                .firstName("Пётр")
                .password("hashed_password")
                .active(false)
                .deactivated(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FindByEmail")
    class FindByEmail {

        @Test
        @DisplayName("Возвращает пользователя, когда email существует")
        void returnsUserWhenEmailExists() {
            // given
            var saved = em.persistAndFlush(aUser("findme@example.com"));

            // when
            Optional<User> result = repository.findByEmail("findme@example.com");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
            assertThat(result.get().getEmail()).isEqualTo("findme@example.com");
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда email не существует")
        void returnsEmptyWhenEmailNotFound() {
            // when
            Optional<User> result = repository.findByEmail("nobody@example.com");

            // then
            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ExistsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("Возвращает true, когда пользователь с таким email существует")
        void returnsTrueWhenEmailExists() {
            // given
            em.persistAndFlush(aUser("exists@example.com"));

            // when / then
            assertThat(repository.existsByEmail("exists@example.com")).isTrue();
        }

        @Test
        @DisplayName("Возвращает false, когда пользователя с таким email нет")
        void returnsFalseWhenEmailNotFound() {
            // when / then
            assertThat(repository.existsByEmail("ghost@example.com")).isFalse();
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("UniqueEmail")
    class UniqueEmail {

        @Test
        @DisplayName("Нарушение уникальности email бросает исключение при flush")
        void throwsOnDuplicateEmail() {
            // given
            em.persistAndFlush(aUser("dup@example.com"));

            // when / then — дубль при flush должен дать ConstraintViolation
            assertThatThrownBy(() -> em.persistAndFlush(aUser("dup@example.com")))
                    .isInstanceOf(Exception.class);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CheckConstraintActiveDeactivated")
    class CheckConstraintActiveDeactivated {

        @Test
        @DisplayName("Нарушение CHECK: active=false + deactivated=null бросает исключение при flush")
        void throwsWhenActiveFalseAndDeactivatedNull() {
            // given — нарушаем инвариант: активность выключена, но дата не проставлена
            var bad = User.builder()
                    .email("bad1@example.com")
                    .password("hashed_password")
                    .active(false)
                    .deactivated(null)  // нарушение: active=false требует deactivated != null
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Нарушение CHECK: active=true + deactivated != null бросает исключение при flush")
        void throwsWhenActiveTrueAndDeactivatedNotNull() {
            // given — нарушаем инвариант: активен, но дата деактивации проставлена
            var bad = User.builder()
                    .email("bad2@example.com")
                    .password("hashed_password")
                    .active(true)
                    .deactivated(Instant.now())  // нарушение: active=true требует deactivated IS NULL
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Корректный soft delete: active=false + deactivated != null сохраняется без ошибок")
        void softDeleteSavesCorrectly() {
            // given / when
            var deactivated = em.persistAndFlush(aDeactivatedUser("softdel@example.com"));

            // then
            assertThat(deactivated.isActive()).isFalse();
            assertThat(deactivated.getDeactivated()).isNotNull();
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("UUID генерируется автоматически при сохранении")
        void uuidIsGeneratedOnSave() {
            // given
            var user = aUser("uuid@example.com");
            assertThat(user.getId()).isNull(); // до сохранения id нет

            // when
            var saved = em.persistFlushFind(user);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Все поля корректно сохраняются и читаются из БД")
        void allFieldsPersistedCorrectly() {
            // given
            var user = User.builder()
                    .email("full@example.com")
                    .lastName("Сидоров")
                    .firstName("Сидор")
                    .middleName("Сидорович")
                    .password("super_hashed")
                    .build();

            // when — persistFlushFind сбрасывает кеш 1-го уровня и перечитывает из БД
            var loaded = em.persistFlushFind(user);

            // then
            assertThat(loaded.getEmail()).isEqualTo("full@example.com");
            assertThat(loaded.getLastName()).isEqualTo("Сидоров");
            assertThat(loaded.getFirstName()).isEqualTo("Сидор");
            assertThat(loaded.getMiddleName()).isEqualTo("Сидорович");
            assertThat(loaded.isActive()).isTrue();
            assertThat(loaded.getDeactivated()).isNull();
            assertThat(loaded.getCreated()).isNotNull()
                    .isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Дефолт active=true выставляется из @Builder.Default")
        void defaultActiveIsTrue() {
            // when
            var saved = em.persistAndFlush(aUser("active-default@example.com"));

            // then
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getDeactivated()).isNull();
        }

        @Test
        @DisplayName("Поле created заполнен после сохранения")
        void createdIsPopulatedAfterSave() {
            // when
            var saved = em.persistAndFlush(aUser("created@example.com"));

            // then
            assertThat(saved.getCreated()).isNotNull();
        }
    }
}
