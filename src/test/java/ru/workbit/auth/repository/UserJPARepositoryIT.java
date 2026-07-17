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
                    .password("hashed_roundtrip")
                    .emailVerified(true)
                    .created(now)
                    .build();

            // when
            var saved = em.persistFlushFind(user);

            // then
            assertThat(saved.getEmail()).isEqualTo("roundtrip@example.com");
            assertThat(saved.getPassword()).isEqualTo("hashed_roundtrip");
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
}
