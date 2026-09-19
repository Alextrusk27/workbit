package ru.workbit.billing.repository;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.billing.model.BillingAccount;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BillingAccountRepositoryIT")
class BillingAccountRepositoryIT extends AbstractPostgresIT {

    private static final int WELCOME_LIMITS = 20;

    @Autowired
    private BillingAccountRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build(); // emailVerified=false, created=now() — @Builder.Default
    }

    private BillingAccount anAccount(UUID userId, int limits, Instant limitsExpireAt) {
        return BillingAccount.builder()
                .userId(userId)
                .limits(limits)
                .limitsExpireAt(limitsExpireAt)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("InsertIfAbsent")
    class InsertIfAbsent {

        @Test
        @DisplayName("Создаёт строку с приветственными лимитами и сроком ≈ now + 3 месяца, если её ещё нет")
        void createsRowWithWelcomeLimitsWhenAbsent() {
            // given
            var user = em.persistAndFlush(aUser("billing-insert-new@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            // when
            int inserted = repository.insertIfAbsent(user.getId(), WELCOME_LIMITS, now);

            // then
            assertThat(inserted).isEqualTo(1);
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(WELCOME_LIMITS);
            assertThat(saved.getLimitsExpireAt())
                    .isBetween(now.plus(Duration.ofDays(89)), now.plus(Duration.ofDays(93)));
            assertThat(saved.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("Идемпотентен: повторный вызов возвращает 0 и не меняет уже существующую строку")
        void idempotentOnUnchangedRow() {
            // given
            var user = em.persistAndFlush(aUser("billing-insert-idempotent@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            repository.insertIfAbsent(user.getId(), WELCOME_LIMITS, now);
            var firstExpireAt = repository.findById(user.getId()).orElseThrow().getLimitsExpireAt();

            // when — повторный вызов с другими значениями
            int inserted = repository.insertIfAbsent(user.getId(), 999, now.plusSeconds(3600));

            // then — исходные значения сохранились
            assertThat(inserted).isZero();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(WELCOME_LIMITS);
            assertThat(saved.getLimitsExpireAt()).isEqualTo(firstExpireAt);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Debit")
    class Debit {

        @Test
        @DisplayName("Списывает и возвращает 1 при достаточном балансе и не истёкшем сроке")
        void debitsWhenBalanceSufficient() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-sufficient@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 100, now.plusSeconds(3600)));

            // when
            int updated = repository.debit(user.getId(), 30, now);

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(70);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет баланс при недостаточном балансе")
        void returnsZeroWhenBalanceInsufficient() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-insufficient@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 10, now.plusSeconds(3600)));

            // when
            int updated = repository.debit(user.getId(), 30, now);

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(10);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет баланс при истёкшем сроке лимитов")
        void returnsZeroWhenExpired() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-expired@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 100, now.minusSeconds(3600)));

            // when
            int updated = repository.debit(user.getId(), 30, now);

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(100);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CreditPack")
    class CreditPack {

        @Test
        @DisplayName("На активном балансе: остаток + пакет, срок = now + 3 месяца, paid_at проставлен")
        void addsPackToActiveBalance() {
            // given
            var user = em.persistAndFlush(aUser("billing-pack-active@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 15, now.plusSeconds(3600)));

            // when
            repository.creditPack(user.getId(), 50, now);

            // then
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(65);
            assertThat(saved.getLimitsExpireAt())
                    .isBetween(now.plus(Duration.ofDays(89)), now.plus(Duration.ofDays(93)));
            assertThat(saved.getPaidAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("На просроченном балансе: старый остаток сгорает, остаётся только пакет")
        void burnsExpiredBalanceAndKeepsOnlyPack() {
            // given
            var user = em.persistAndFlush(aUser("billing-pack-expired@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 15, now.minusSeconds(3600)));

            // when
            repository.creditPack(user.getId(), 50, now);

            // then
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(50);
            assertThat(saved.getLimitsExpireAt())
                    .isBetween(now.plus(Duration.ofDays(89)), now.plus(Duration.ofDays(93)));
            assertThat(saved.getPaidAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("paid_at не перезаписывается повторной покупкой")
        void doesNotOverwritePaidAtOnRepeatedPurchase() {
            // given
            var user = em.persistAndFlush(aUser("billing-pack-paid-at@example.com"));
            var firstNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
            em.persistAndFlush(anAccount(user.getId(), 0, null));
            repository.creditPack(user.getId(), 50, firstNow);
            em.clear();
            var paidAtAfterFirstPurchase = repository.findById(user.getId()).orElseThrow().getPaidAt();

            // when — вторая покупка позже
            var secondNow = firstNow.plusSeconds(3600);
            repository.creditPack(user.getId(), 50, secondNow);

            // then
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPaidAt()).isEqualTo(paidAtAfterFirstPurchase);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("CreditGift")
    class CreditGift {

        @Test
        @DisplayName("Прибавляет лимиты и не меняет срок действия")
        void addsLimitsWithoutChangingExpiry() {
            // given
            var user = em.persistAndFlush(aUser("billing-gift@example.com"));
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            var expireAt = now.plusSeconds(3600);
            em.persistAndFlush(anAccount(user.getId(), 10, expireAt));

            // when
            repository.creditGift(user.getId(), 5);

            // then
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getLimits()).isEqualTo(15);
            assertThat(saved.getLimitsExpireAt()).isEqualTo(expireAt);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("chk_account_limits: отрицательный баланс бросает исключение при flush")
        void throwsOnNegativeLimits() {
            // given
            var user = em.persistAndFlush(aUser("billing-constraint-negative@example.com"));
            var bad = anAccount(user.getId(), -1, Instant.now().plusSeconds(3600));

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("chk_account_limits: положительный баланс без срока действия бросает исключение при flush")
        void throwsOnPositiveLimitsWithoutExpiry() {
            // given
            var user = em.persistAndFlush(aUser("billing-constraint-no-expiry@example.com"));
            var bad = anAccount(user.getId(), 10, null);

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("ON DELETE CASCADE: удаление пользователя из auth.users удаляет billing.account")
        void cascadeDeleteRemovesAccountOnUserDelete() {
            // given
            var user = em.persistAndFlush(aUser("billing-cascade-del@example.com"));
            var userId = user.getId();
            em.persistAndFlush(anAccount(userId, 10, Instant.now().plusSeconds(3600)));

            // when — физическое удаление пользователя через managed-ссылку.
            // em.clear() перед remove: иначе managed BillingAccount в контексте персистентности
            // при flush выбросит TransientPropertyValueException — она ссылается на User в
            // состоянии removed, который Hibernate воспринимает как transient.
            em.flush();
            em.clear();
            var managed = requireNonNull(em.find(User.class, userId));
            em.remove(managed);
            em.flush();
            em.clear();

            // then — строка аккаунта удалена каскадно
            Long count = ((Number) em.getEntityManager()
                    .createNativeQuery("SELECT COUNT(*) FROM billing.account WHERE user_id = :id")
                    .setParameter("id", userId)
                    .getSingleResult())
                    .longValue();
            assertThat(count).isZero();
        }
    }
}
