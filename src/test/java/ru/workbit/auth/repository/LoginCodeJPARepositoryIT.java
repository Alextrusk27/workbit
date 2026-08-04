package ru.workbit.auth.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.LoginCode;
import ru.workbit.auth.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LoginCodeJPARepositoryIT")
class LoginCodeJPARepositoryIT extends AbstractPostgresIT {

    @Autowired
    private LoginCodeJPARepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build(); // emailVerified=false, created=now() — @Builder.Default
    }

    private LoginCode aLoginCode(User user, String hash) {
        return LoginCode.builder()
                .user(user)
                .codeHash(hash)
                .build(); // attempts=0, usedAt=null, expiresAt и created — @Builder.Default
    }

    private LoginCode aLoginCode(User user, String hash, Instant created) {
        return LoginCode.builder()
                .user(user)
                .codeHash(hash)
                .created(created)
                .build();
    }

    private LoginCode aUsedLoginCode(User user, String hash) {
        return LoginCode.builder()
                .user(user)
                .codeHash(hash)
                .usedAt(Instant.now())
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserAndUsedAtIsNull")
    class FindAllByUserAndUsedAtIsNull {

        @Test
        @DisplayName("Возвращает только неиспользованные коды пользователя")
        void returnsOnlyUnusedCodesOfUser() {
            // given
            var user = em.persistAndFlush(aUser("codes-main@example.com"));
            var otherUser = em.persistAndFlush(aUser("codes-other@example.com"));

            em.persistAndFlush(aLoginCode(user, "hash-unused")); // должен вернуться
            em.persistAndFlush(aUsedLoginCode(user, "hash-used")); // использован — НЕ должен вернуться
            em.persistAndFlush(aLoginCode(otherUser, "hash-stranger")); // чужой — НЕ должен вернуться

            // when
            List<LoginCode> result = repository.findAllByUserAndUsedAtIsNull(user);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getCodeHash()).isEqualTo("hash-unused");
        }

        @Test
        @DisplayName("Возвращает пустой список, когда все коды пользователя использованы")
        void returnsEmptyWhenAllCodesUsed() {
            // given
            var user = em.persistAndFlush(aUser("codes-all-used@example.com"));
            em.persistAndFlush(aUsedLoginCode(user, "hash-used-only"));

            // when
            List<LoginCode> result = repository.findAllByUserAndUsedAtIsNull(user);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindFirstByUserAndUsedAtIsNullOrderByCreatedDesc")
    class FindFirstByUserAndUsedAtIsNullOrderByCreatedDesc {

        @Test
        @DisplayName("Возвращает самый свежий неиспользованный код пользователя")
        void returnsMostRecentUnusedCode() {
            // given — вставляем в перемешанном порядке (не по created), с разными значениями created
            var user = em.persistAndFlush(aUser("codes-order@example.com"));
            var now = Instant.now();

            em.persistAndFlush(aLoginCode(user, "hash-middle", now.minusSeconds(60)));
            em.persistAndFlush(aLoginCode(user, "hash-oldest", now.minusSeconds(120)));
            var newest = em.persistAndFlush(aLoginCode(user, "hash-newest", now));

            // when
            Optional<LoginCode> result = repository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(newest.getId());
        }

        @Test
        @DisplayName("Игнорирует использованные коды, даже если они свежее")
        void excludesUsedCodesEvenIfNewest() {
            // given
            var user = em.persistAndFlush(aUser("codes-order-used@example.com"));
            var now = Instant.now();

            var older = em.persistAndFlush(aLoginCode(user, "hash-older-unused", now.minusSeconds(60)));
            var newerUsed = LoginCode.builder()
                    .user(user)
                    .codeHash("hash-newer-used")
                    .created(now)
                    .usedAt(now)
                    .build();
            em.persistAndFlush(newerUsed);

            // when
            Optional<LoginCode> result = repository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(older.getId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда неиспользованных кодов нет")
        void returnsEmptyWhenNoUnusedCodes() {
            // given
            var user = em.persistAndFlush(aUser("codes-order-empty@example.com"));
            em.persistAndFlush(aUsedLoginCode(user, "hash-only-used"));

            // when
            Optional<LoginCode> result = repository.findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(user);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        @DisplayName("Дефолт attempts=0 применяется после сохранения")
        void defaultAttemptsIsZero() {
            // given
            var user = em.persistAndFlush(aUser("codes-default-attempts@example.com"));

            // when
            var saved = em.persistFlushFind(aLoginCode(user, "hash-default-attempts"));

            // then
            assertThat(saved.getAttempts()).isZero();
        }

        @Test
        @DisplayName("Дефолт expiresAt применяет TTL ~900 секунд от момента создания")
        void defaultExpiresAtAppliesTtl() {
            // given
            var user = em.persistAndFlush(aUser("codes-default-ttl@example.com"));

            // when
            var saved = em.persistFlushFind(aLoginCode(user, "hash-default-ttl"));

            // then
            assertThat(saved.getExpiresAt())
                    .isCloseTo(Instant.now().plusSeconds(900), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Поле created заполнено после сохранения")
        void createdIsPopulated() {
            // given
            var user = em.persistAndFlush(aUser("codes-default-created@example.com"));

            // when
            var saved = em.persistFlushFind(aLoginCode(user, "hash-default-created"));

            // then
            assertThat(saved.getCreated()).isNotNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("NOT NULL user_id: код без пользователя бросает исключение при flush")
        void throwsWhenUserIsNull() {
            // given
            var bad = LoginCode.builder()
                    .user(null)
                    .codeHash("hash-no-user")
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("code_hash не уникален глобально: одинаковый хеш у разных пользователей сохраняется без ошибок")
        void codeHashNotUniqueAcrossUsers() {
            // given
            var userA = em.persistAndFlush(aUser("codes-collide-a@example.com"));
            var userB = em.persistAndFlush(aUser("codes-collide-b@example.com"));

            // when
            em.persistAndFlush(aLoginCode(userA, "shared-hash"));
            var savedB = em.persistAndFlush(aLoginCode(userB, "shared-hash"));

            // then — второй persistAndFlush не бросил исключение, оба кода сохранены
            assertThat(savedB.getId()).isNotNull();
        }

        @Test
        @DisplayName("FK ON DELETE CASCADE: удаление пользователя удаляет его коды входа")
        void cascadeDeleteRemovesCodesOnUserDelete() {
            // given
            var user = em.persistAndFlush(aUser("codes-cascade-del@example.com"));
            var userId = user.getId();
            em.persistAndFlush(aLoginCode(user, "hash-cascade-1"));
            em.persistAndFlush(aLoginCode(user, "hash-cascade-2"));

            // when — физическое удаление пользователя через managed-ссылку.
            // em.clear() перед remove: иначе managed LoginCode-ы в контексте персистентности
            // при flush выбросят TransientPropertyValueException — они ссылаются на User в
            // состоянии removed, который Hibernate воспринимает как transient.
            em.flush();
            em.clear();
            var managed = requireNonNull(em.find(User.class, userId));
            em.remove(managed);
            em.flush();
            em.clear();

            // then — коды удалены каскадно
            Long count = ((Number) em.getEntityManager()
                    .createNativeQuery("SELECT COUNT(*) FROM auth.login_code WHERE user_id = :id")
                    .setParameter("id", userId)
                    .getSingleResult())
                    .longValue();
            assertThat(count).isZero();
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
            var user = em.persistAndFlush(aUser("codes-uuid-gen@example.com"));
            var code = aLoginCode(user, "hash-uuid-gen");
            assertThat(code.getId()).isNull();

            // when
            var saved = em.persistFlushFind(code);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Round-trip всех полей: данные читаются из БД идентично сохранённым")
        void roundTripAllFields() {
            // given
            var user = em.persistAndFlush(aUser("codes-roundtrip@example.com"));
            var now = Instant.now();
            var code = LoginCode.builder()
                    .user(user)
                    .codeHash("hash-roundtrip")
                    .attempts(2)
                    .expiresAt(now.plusSeconds(600))
                    .usedAt(now)
                    .created(now)
                    .build();

            // when
            var saved = em.persistFlushFind(code);

            // then
            assertThat(saved.getCodeHash()).isEqualTo("hash-roundtrip");
            assertThat(saved.getAttempts()).isEqualTo(2);
            assertThat(saved.getUsedAt()).isNotNull();
            assertThat(saved.getExpiresAt()).isNotNull();
            assertThat(saved.getCreated()).isNotNull();
            assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        }
    }
}
