package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.RefreshToken;
import ru.workbit.auth.repository.RefreshTokenJPARepository;
import ru.workbit.user.model.User;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RefreshTokenJPARepositoryIT")
class RefreshTokenJPARepositoryIT extends AbstractPostgresIT {

    @Autowired
    private RefreshTokenJPARepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .password("hashed_password")
                .build(); // active=true, deactivated=null по @Builder.Default
    }

    private RefreshToken aToken(User user, String hash) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .build(); // expiresAt, revoked, created — @Builder.Default
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByTokenHash")
    class FindByTokenHash {

        @Test
        @DisplayName("Возвращает токен, когда хеш существует")
        void returnsTokenWhenHashExists() {
            // given
            var user = em.persistAndFlush(aUser("find-token@example.com"));
            var token = em.persistAndFlush(aToken(user, "hash-exists"));

            // when
            Optional<RefreshToken> result = repository.findByTokenHash("hash-exists");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(token.getId());
            assertThat(result.get().getTokenHash()).isEqualTo("hash-exists");
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда хеш не существует")
        void returnsEmptyWhenHashNotFound() {
            // when
            Optional<RefreshToken> result = repository.findByTokenHash("no-such-hash");

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("RevokeAllByUser")
    class RevokeAllByUser {

        @Test
        @DisplayName("Помечает все токены пользователя как revoked=true")
        void revokesAllTokensOfUser() {
            // given
            var user = em.persistAndFlush(aUser("revoke-all@example.com"));
            em.persistAndFlush(aToken(user, "hash-a"));
            em.persistAndFlush(aToken(user, "hash-b"));

            // when
            em.flush();
            repository.revokeAllByUser(user);
            em.flush();
            em.clear(); // обязателен: bulk-update не обновляет кэш 1-го уровня

            // then
            var tokens = repository.findAll().stream()
                    .filter(t -> t.getUser().getId().equals(user.getId()))
                    .toList();
            assertThat(tokens).hasSize(2);
            assertThat(tokens).allMatch(RefreshToken::isRevoked);
        }

        @Test
        @DisplayName("Не трогает токены другого пользователя")
        void doesNotRevokeOtherUsersTokens() {
            // given
            var userA = em.persistAndFlush(aUser("revoke-owner@example.com"));
            var userB = em.persistAndFlush(aUser("revoke-other@example.com"));
            em.persistAndFlush(aToken(userA, "hash-owner"));
            em.persistAndFlush(aToken(userB, "hash-other"));

            // when
            repository.revokeAllByUser(userA);
            em.flush();
            em.clear();

            // then — токен userB остался нетронутым
            var tokenB = repository.findByTokenHash("hash-other");
            assertThat(tokenB).isPresent();
            assertThat(tokenB.get().isRevoked()).isFalse();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("RevokeToken")
    class RevokeToken {

        @Test
        @DisplayName("Помечает конкретный токен по хешу как revoked=true")
        void revokesSpecificToken() {
            // given
            var user = em.persistAndFlush(aUser("revoke-one@example.com"));
            em.persistAndFlush(aToken(user, "hash-target"));
            em.persistAndFlush(aToken(user, "hash-untouched"));

            // when
            repository.revokeToken("hash-target");
            em.flush();
            em.clear();

            // then — целевой отозван
            var target = repository.findByTokenHash("hash-target");
            assertThat(target).isPresent();
            assertThat(target.get().isRevoked()).isTrue();
        }

        @Test
        @DisplayName("Не трогает остальные токены")
        void leavesOtherTokensUntouched() {
            // given
            var user = em.persistAndFlush(aUser("revoke-one-other@example.com"));
            em.persistAndFlush(aToken(user, "hash-main"));
            em.persistAndFlush(aToken(user, "hash-other2"));

            // when
            repository.revokeToken("hash-main");
            em.flush();
            em.clear();

            // then — второй токен не отозван
            var other = repository.findByTokenHash("hash-other2");
            assertThat(other).isPresent();
            assertThat(other.get().isRevoked()).isFalse();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("Нарушение UNIQUE token_hash бросает исключение при flush")
        void throwsOnDuplicateTokenHash() {
            // given
            var user = em.persistAndFlush(aUser("dup-hash@example.com"));
            em.persistAndFlush(aToken(user, "duplicate-hash"));

            // when / then — второй токен с тем же хешем
            assertThatThrownBy(() -> em.persistAndFlush(aToken(user, "duplicate-hash")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("NOT NULL user_id: токен без пользователя бросает исключение при flush")
        void throwsWhenUserIsNull() {
            // given
            var bad = RefreshToken.builder()
                    .user(null)
                    .tokenHash("no-user-hash")
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("FK ON DELETE CASCADE: удаление пользователя удаляет его refresh-токены")
        void cascadeDeleteRemovesTokensOnUserDelete() {
            // given
            var user = em.persistAndFlush(aUser("cascade-del@example.com"));
            em.persistAndFlush(aToken(user, "hash-cascade-1"));
            em.persistAndFlush(aToken(user, "hash-cascade-2"));

            // when — физическое удаление пользователя (только для проверки FK CASCADE в тесте БД,
            //         в production используется soft delete).
            // em.clear() перед remove: иначе managed RefreshToken-ы в контексте персистентности
            // при flush выбросят TransientPropertyValueException — они ссылаются на User в
            // состоянии removed, который Hibernate воспринимает как transient.
            em.flush();
            em.clear();
            var managed = requireNonNull(em.find(User.class, user.getId()));
            em.remove(managed);
            em.flush();
            em.clear();

            // then — токены удалены каскадно
            assertThat(repository.findByTokenHash("hash-cascade-1")).isEmpty();
            assertThat(repository.findByTokenHash("hash-cascade-2")).isEmpty();
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
            var user = em.persistAndFlush(aUser("uuid-gen@example.com"));
            var token = aToken(user, "hash-uuid");
            assertThat(token.getId()).isNull();

            // when
            var saved = em.persistFlushFind(token);

            // then
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("Дефолт revoked=false применяется после сохранения")
        void defaultRevokedIsFalse() {
            // given
            var user = em.persistAndFlush(aUser("default-revoked@example.com"));

            // when
            var saved = em.persistFlushFind(aToken(user, "hash-default-revoked"));

            // then
            assertThat(saved.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("Поля expiresAt и created заполнены после сохранения")
        void timestampsArePopulatedAfterSave() {
            // given
            var user = em.persistAndFlush(aUser("timestamps@example.com"));

            // when
            var saved = em.persistFlushFind(aToken(user, "hash-timestamps"));

            // then
            assertThat(saved.getExpiresAt()).isNotNull().isAfter(Instant.now());
            assertThat(saved.getCreated()).isNotNull();
        }
    }
}
