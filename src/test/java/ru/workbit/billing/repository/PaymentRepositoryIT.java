package ru.workbit.billing.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import ru.workbit.AbstractPostgresIT;
import ru.workbit.auth.model.User;
import ru.workbit.billing.model.Payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PaymentRepositoryIT")
class PaymentRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private TestEntityManager em;

    // --- фабрики ---

    private User aUser(String email) {
        return User.builder()
                .email(email)
                .build();
    }

    private Payment aPayment(UUID userId, int invId) {
        return aPayment(userId, invId, Payment.Status.PENDING);
    }

    private Payment aPayment(UUID userId, int invId, Payment.Status status) {
        return Payment.builder()
                .invId(invId)
                .userId(userId)
                .product(Payment.Product.PLAN_PRO)
                .amount(Payment.Product.PLAN_PRO.getPrice())
                .status(status)
                .build(); // created — @Builder.Default
    }

    // =========================================================================

    @Nested
    @DisplayName("SaveAndRead")
    class SaveAndRead {

        @Test
        @DisplayName("Сохраняет платёж и читает его обратно с корректным маппингом колонок и enum'ов")
        void savesAndReadsAllFields() {
            // given
            var user = em.persistAndFlush(aUser("payment-save-read@example.com"));
            int invId = repository.nextInvId();
            var payment = aPayment(user.getId(), invId);

            // when
            var saved = em.persistFlushFind(payment);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getInvId()).isEqualTo(invId);
            assertThat(saved.getUserId()).isEqualTo(user.getId());
            assertThat(saved.getProduct()).isEqualTo(Payment.Product.PLAN_PRO);
            assertThat(saved.getAmount()).isEqualByComparingTo(Payment.Product.PLAN_PRO.getPrice());
            assertThat(saved.getStatus()).isEqualTo(Payment.Status.PENDING);
            assertThat(saved.getCreated()).isNotNull();
            assertThat(saved.getPaidAt()).isNull();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("NextInvId")
    class NextInvId {

        @Test
        @DisplayName("Два последовательных вызова дают возрастающие значения")
        void returnsIncreasingValuesOnSuccessiveCalls() {
            // when
            int first = repository.nextInvId();
            int second = repository.nextInvId();

            // then
            assertThat(second).isEqualTo(first + 1);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("FindByInvId")
    class FindByInvId {

        @Test
        @DisplayName("Находит платёж по inv_id")
        void findsPaymentByInvId() {
            // given
            var user = em.persistAndFlush(aUser("payment-find-by-invid@example.com"));
            int invId = repository.nextInvId();
            var payment = em.persistAndFlush(aPayment(user.getId(), invId));

            // when
            Optional<Payment> result = repository.findByInvId(invId);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(payment.getId());
        }

        @Test
        @DisplayName("Возвращает Optional.empty() для неизвестного inv_id")
        void returnsEmptyForUnknownInvId() {
            // when / then
            assertThat(repository.findByInvId(Integer.MAX_VALUE)).isEmpty();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("MarkPaid")
    class MarkPaid {

        @Test
        @DisplayName("Переводит PENDING-платёж в PAID, проставляет paid_at и возвращает 1")
        void marksPendingPaymentAsPaid() {
            // given
            var user = em.persistAndFlush(aUser("payment-mark-paid@example.com"));
            var payment = em.persistAndFlush(aPayment(user.getId(), repository.nextInvId()));
            var paidAt = Instant.now();

            // when
            int updated = repository.markPaid(payment.getId(), paidAt);

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(payment.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(Payment.Status.PAID);
            assertThat(saved.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("Повторный вызов на уже оплаченном платеже возвращает 0 и не перезаписывает paid_at")
        void doesNotOverwritePaidAtOnRepeatedCall() {
            // given
            var user = em.persistAndFlush(aUser("payment-mark-paid-repeat@example.com"));
            var payment = em.persistAndFlush(aPayment(user.getId(), repository.nextInvId()));
            repository.markPaid(payment.getId(), Instant.now());
            em.clear();
            var afterFirstCall = repository.findById(payment.getId()).orElseThrow().getPaidAt();

            // when — повторный вызов с другим временем
            int updated = repository.markPaid(payment.getId(), Instant.now().plusSeconds(3600));

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(payment.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(Payment.Status.PAID);
            assertThat(saved.getPaidAt()).isEqualTo(afterFirstCall);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("Уникальность inv_id: второй платёж с тем же inv_id бросает DataIntegrityViolationException")
        void throwsOnDuplicateInvId() {
            // given
            var user = em.persistAndFlush(aUser("payment-dup-invid@example.com"));
            int invId = repository.nextInvId();
            em.persistAndFlush(aPayment(user.getId(), invId));
            var duplicate = aPayment(user.getId(), invId);

            // when / then
            assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("ON DELETE CASCADE: удаление пользователя из auth.users удаляет его платежи")
        void cascadeDeleteRemovesPaymentsOnUserDelete() {
            // given
            var user = em.persistAndFlush(aUser("payment-cascade-del@example.com"));
            var userId = user.getId();
            var payment = em.persistAndFlush(aPayment(userId, repository.nextInvId()));
            var paymentId = payment.getId();

            // when — физическое удаление пользователя через managed-ссылку.
            // em.clear() перед remove: иначе managed Payment в контексте персистентности
            // при flush может выбросить TransientPropertyValueException.
            em.flush();
            em.clear();
            var managed = requireNonNull(em.find(User.class, userId));
            em.remove(managed);
            em.flush();
            em.clear();

            // then
            assertThat(repository.findById(paymentId)).isEmpty();
        }
    }
}
