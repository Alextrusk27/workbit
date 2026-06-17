package ru.workbit.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.VerificationToken;
import ru.workbit.auth.repository.VerificationTokenJPARepository;
import ru.workbit.user.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("VerificationTokenJPARepositoryIT")
class VerificationTokenJPARepositoryIT extends AbstractPostgresIT {

    @Autowired
    private VerificationTokenJPARepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .password("hashed_password")
                .build(); // active=true, deactivated=null — доменный инвариант
    }

    private VerificationToken aToken(User user, String hash, VerificationToken.Type type) {
        return VerificationToken.builder()
                .user(user)
                .tokenHash(hash)
                .type(type)
                .build(); // usedAt=null, expiresAt и created — @Builder.Default
    }

    private VerificationToken aUsedToken(User user, String hash, VerificationToken.Type type) {
        return VerificationToken.builder()
                .user(user)
                .tokenHash(hash)
                .type(type)
                .usedAt(Instant.now())
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByTokenHash")
    class FindByTokenHash {

        @Test
        @DisplayName("Возвращает токен, когда tokenHash существует")
        void returnsTokenWhenHashExists() {
            // given
            var user = em.persistAndFlush(aUser("find-token@example.com"));
            em.persistAndFlush(aToken(user, "hash-abc", VerificationToken.Type.EMAIL_VERIFICATION));

            // when
            Optional<VerificationToken> result = repository.findByTokenHash("hash-abc");

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getTokenHash()).isEqualTo("hash-abc");
            assertThat(result.get().getUser().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty(), когда tokenHash не существует")
        void returnsEmptyWhenHashNotFound() {
            // when
            Optional<VerificationToken> result = repository.findByTokenHash("nonexistent-hash");

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindAllByUserAndTypeAndUsedAtIsNull")
    class FindAllByUserAndTypeAndUsedAtIsNull {

        @Test
        @DisplayName("Возвращает только неиспользованные токены нужного пользователя и типа")
        void returnsOnlyUnusedTokensForUserAndType() {
            // given
            var user = em.persistAndFlush(aUser("filter-main@example.com"));
            var otherUser = em.persistAndFlush(aUser("filter-other@example.com"));

            // неиспользованный EMAIL_VERIFICATION для user — должен вернуться
            em.persistAndFlush(aToken(user, "hash-1", VerificationToken.Type.EMAIL_VERIFICATION));

            // использованный EMAIL_VERIFICATION для user — НЕ должен вернуться
            em.persistAndFlush(aUsedToken(user, "hash-2", VerificationToken.Type.EMAIL_VERIFICATION));

            // неиспользованный PASSWORD_RESET для user — НЕ должен вернуться (другой type)
            em.persistAndFlush(aToken(user, "hash-3", VerificationToken.Type.PASSWORD_RESET));

            // неиспользованный EMAIL_VERIFICATION для другого пользователя — НЕ должен вернуться
            em.persistAndFlush(aToken(otherUser, "hash-4", VerificationToken.Type.EMAIL_VERIFICATION));

            // when
            List<VerificationToken> result = repository.findAllByUserAndTypeAndUsedAtIsNull(
                    user, VerificationToken.Type.EMAIL_VERIFICATION);

            // then — ровно один токен: hash-1
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTokenHash()).isEqualTo("hash-1");
        }

        @Test
        @DisplayName("Фильтрует по типу: другой type не попадает в результат")
        void filtersByType() {
            // given
            var user = em.persistAndFlush(aUser("type-filter@example.com"));
            em.persistAndFlush(aToken(user, "hash-pr", VerificationToken.Type.PASSWORD_RESET));
            em.persistAndFlush(aToken(user, "hash-ev", VerificationToken.Type.EMAIL_VERIFICATION));

            // when
            List<VerificationToken> result = repository.findAllByUserAndTypeAndUsedAtIsNull(
                    user, VerificationToken.Type.PASSWORD_RESET);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTokenHash()).isEqualTo("hash-pr");
        }

        @Test
        @DisplayName("Использованные токены (usedAt != null) не попадают в результат")
        void filtersOutUsedTokens() {
            // given
            var user = em.persistAndFlush(aUser("used-filter@example.com"));
            em.persistAndFlush(aUsedToken(user, "hash-used", VerificationToken.Type.PASSWORD_RESET));

            // when
            List<VerificationToken> result = repository.findAllByUserAndTypeAndUsedAtIsNull(
                    user, VerificationToken.Type.PASSWORD_RESET);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Токены другого пользователя не попадают в результат")
        void filtersOutOtherUsersTokens() {
            // given
            var user = em.persistAndFlush(aUser("owner@example.com"));
            var stranger = em.persistAndFlush(aUser("stranger@example.com"));
            em.persistAndFlush(aToken(stranger, "hash-stranger", VerificationToken.Type.EMAIL_VERIFICATION));

            // when
            List<VerificationToken> result = repository.findAllByUserAndTypeAndUsedAtIsNull(
                    user, VerificationToken.Type.EMAIL_VERIFICATION);

            // then
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("UniqueTokenHash")
    class UniqueTokenHash {

        @Test
        @DisplayName("Дублирующий tokenHash бросает исключение при flush")
        void throwsOnDuplicateTokenHash() {
            // given
            var user = em.persistAndFlush(aUser("dup-hash@example.com"));
            em.persistAndFlush(aToken(user, "dup-hash-value", VerificationToken.Type.EMAIL_VERIFICATION));

            // when / then
            assertThatThrownBy(() ->
                    em.persistAndFlush(aToken(user, "dup-hash-value", VerificationToken.Type.PASSWORD_RESET)))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("NotNullUserId")
    class NotNullUserId {

        @Test
        @DisplayName("Токен без user_id (user=null) бросает исключение при flush")
        void throwsWhenUserIsNull() {
            // given — намеренно нарушаем NOT NULL FK
            var bad = VerificationToken.builder()
                    .user(null)
                    .tokenHash("hash-no-user")
                    .type(VerificationToken.Type.EMAIL_VERIFICATION)
                    .build();

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("EnumTypeMapping")
    class EnumTypeMapping {

        @Test
        @DisplayName("Тип PASSWORD_RESET сохраняется как строка и корректно читается из БД")
        void passwordResetEnumRoundTrip() {
            // given
            var user = em.persistAndFlush(aUser("enum-pr@example.com"));
            var token = em.persistFlushFind(aToken(user, "hash-pr-enum", VerificationToken.Type.PASSWORD_RESET));

            // then
            assertThat(token.getType()).isEqualTo(VerificationToken.Type.PASSWORD_RESET);
        }

        @Test
        @DisplayName("Тип EMAIL_VERIFICATION сохраняется как строка и корректно читается из БД")
        void emailVerificationEnumRoundTrip() {
            // given
            var user = em.persistAndFlush(aUser("enum-ev@example.com"));
            var token = em.persistFlushFind(aToken(user, "hash-ev-enum", VerificationToken.Type.EMAIL_VERIFICATION));

            // then
            assertThat(token.getType()).isEqualTo(VerificationToken.Type.EMAIL_VERIFICATION);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FkCascadeDeleteOnUser")
    class FkCascadeDeleteOnUser {

        @Test
        @DisplayName("Удаление пользователя каскадно удаляет его verification-токены (ON DELETE CASCADE)")
        void cascadeDeleteRemovesTokens() {
            // given
            var user = em.persistAndFlush(aUser("cascade-del@example.com"));
            var userId = user.getId();
            em.persistAndFlush(aToken(user, "hash-cascade-1", VerificationToken.Type.EMAIL_VERIFICATION));
            em.persistAndFlush(aToken(user, "hash-cascade-2", VerificationToken.Type.PASSWORD_RESET));

            // when — удаляем пользователя напрямую через EntityManager (минуем JPA-каскад)
            em.getEntityManager().createNativeQuery(
                            "DELETE FROM auth.users WHERE id = :id")
                    .setParameter("id", userId)
                    .executeUpdate();
            em.flush();

            // then — токены должны исчезнуть из-за ON DELETE CASCADE в схеме
            assertThat(repository.findByTokenHash("hash-cascade-1")).isEmpty();
            assertThat(repository.findByTokenHash("hash-cascade-2")).isEmpty();
        }
    }
}
