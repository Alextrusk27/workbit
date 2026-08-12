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
import ru.workbit.billing.model.BillingAccount;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BillingAccountRepositoryIT")
class BillingAccountRepositoryIT extends AbstractPostgresIT {

    private static final int FREE_INTERVIEWS = BillingAccount.Plan.FREE.getInterviews();
    private static final int FREE_TRAININGS = BillingAccount.Plan.FREE.getTrainings();

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

    private BillingAccount aFreeAccount(UUID userId, int planInterviewsLeft, int planTrainingsLeft) {
        return BillingAccount.builder()
                .userId(userId)
                .plan(BillingAccount.Plan.FREE)
                .planInterviewsLeft(planInterviewsLeft)
                .planTrainingsLeft(planTrainingsLeft)
                .packInterviewsLeft(0)
                .packTrainingsLeft(0)
                .build();
    }

    private BillingAccount aProAccount(UUID userId, Instant planExpiresAt, int planInterviewsLeft,
                                        int planTrainingsLeft, int packInterviewsLeft, int packTrainingsLeft) {
        return BillingAccount.builder()
                .userId(userId)
                .plan(BillingAccount.Plan.PRO)
                .planExpiresAt(planExpiresAt)
                .planInterviewsLeft(planInterviewsLeft)
                .planTrainingsLeft(planTrainingsLeft)
                .packInterviewsLeft(packInterviewsLeft)
                .packTrainingsLeft(packTrainingsLeft)
                .build();
    }

    // =========================================================================

    @Nested
    @DisplayName("InsertIfAbsent")
    class InsertIfAbsent {

        @Test
        @DisplayName("Создаёт FREE-строку с дефолтными остатками, если её ещё нет")
        void createsFreeRowWhenAbsent() {
            // given
            var user = em.persistAndFlush(aUser("billing-insert-new@example.com"));

            // when
            repository.insertIfAbsent(user.getId(), FREE_INTERVIEWS, FREE_TRAININGS);

            // then
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlan()).isEqualTo(BillingAccount.Plan.FREE);
            assertThat(saved.getPlanExpiresAt()).isNull();
            assertThat(saved.getPlanInterviewsLeft()).isEqualTo(FREE_INTERVIEWS);
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(FREE_TRAININGS);
            assertThat(saved.getPackInterviewsLeft()).isZero();
            assertThat(saved.getPackTrainingsLeft()).isZero();
        }

        @Test
        @DisplayName("Идемпотентен: повторный вызов не падает и не меняет уже существующую строку")
        void idempotentOnUnchangedRow() {
            // given
            var user = em.persistAndFlush(aUser("billing-insert-idempotent@example.com"));
            repository.insertIfAbsent(user.getId(), FREE_INTERVIEWS, FREE_TRAININGS);

            // when — повторный вызов с другими значениями (как будто для другого тарифа)
            repository.insertIfAbsent(user.getId(), 25, 50);

            // then — исходные значения сохранились
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isEqualTo(FREE_INTERVIEWS);
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(FREE_TRAININGS);
        }

        @Test
        @DisplayName("Идемпотентен: повторный вызов не восстанавливает уже списанный остаток")
        void idempotentOnDebitedRow() {
            // given
            var user = em.persistAndFlush(aUser("billing-insert-idempotent-debited@example.com"));
            repository.insertIfAbsent(user.getId(), FREE_INTERVIEWS, FREE_TRAININGS);
            repository.debitPlanInterview(user.getId(), Instant.now());

            // when — повторный вызов после того, как остаток уже списан
            repository.insertIfAbsent(user.getId(), FREE_INTERVIEWS, FREE_TRAININGS);

            // then — списанный остаток не восстановлен
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isZero();
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(FREE_TRAININGS);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DebitPlanInterview")
    class DebitPlanInterview {

        @Test
        @DisplayName("Списывает 1 и возвращает 1 при активном FREE-плане (без даты истечения)")
        void debitsOnActiveFreePlan() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-interview-free@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 1, 3));

            // when
            int updated = repository.debitPlanInterview(user.getId(), Instant.now());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isZero();
        }

        @Test
        @DisplayName("Списывает 1 и возвращает 1 при активном PRO-плане с датой истечения в будущем")
        void debitsOnActiveProPlan() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-interview-pro@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().plusSeconds(3600), 5, 5, 0, 0));

            // when
            int updated = repository.debitPlanInterview(user.getId(), Instant.now());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isEqualTo(4);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при нулевом plan_interviews_left")
        void returnsZeroWhenNoInterviewsLeft() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-interview-zero@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 0, 3));

            // when
            int updated = repository.debitPlanInterview(user.getId(), Instant.now());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isZero();
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при истёкшем PRO-плане")
        void returnsZeroWhenProPlanExpired() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-interview-expired@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().minusSeconds(3600), 5, 5, 0, 0));

            // when
            int updated = repository.debitPlanInterview(user.getId(), Instant.now());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanInterviewsLeft()).isEqualTo(5);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DebitPlanTraining")
    class DebitPlanTraining {

        @Test
        @DisplayName("Списывает 1 и возвращает 1 при активном FREE-плане (без даты истечения)")
        void debitsOnActiveFreePlan() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-training-free@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 1, 3));

            // when
            int updated = repository.debitPlanTraining(user.getId(), Instant.now());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(2);
        }

        @Test
        @DisplayName("Списывает 1 и возвращает 1 при активном PRO-плане с датой истечения в будущем")
        void debitsOnActiveProPlan() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-training-pro@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().plusSeconds(3600), 5, 5, 0, 0));

            // when
            int updated = repository.debitPlanTraining(user.getId(), Instant.now());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(4);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при нулевом plan_trainings_left")
        void returnsZeroWhenNoTrainingsLeft() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-training-zero@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 1, 0));

            // when
            int updated = repository.debitPlanTraining(user.getId(), Instant.now());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanTrainingsLeft()).isZero();
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при истёкшем PRO-плане")
        void returnsZeroWhenProPlanExpired() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-plan-training-expired@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().minusSeconds(3600), 5, 5, 0, 0));

            // when
            int updated = repository.debitPlanTraining(user.getId(), Instant.now());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPlanTrainingsLeft()).isEqualTo(5);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DebitPackInterview")
    class DebitPackInterview {

        @Test
        @DisplayName("Списывает pack-остаток независимо от плана и истёкшего срока")
        void debitsRegardlessOfExpiredPlan() {
            // given — PRO с истёкшим plan_expires_at: списание пакета не зависит от активности плана
            var user = em.persistAndFlush(aUser("billing-debit-pack-interview-expired@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().minusSeconds(3600), 0, 0, 2, 5));

            // when
            int updated = repository.debitPackInterview(user.getId());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPackInterviewsLeft()).isEqualTo(1);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при нулевом pack_interviews_left")
        void returnsZeroWhenNoPackInterviewsLeft() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-pack-interview-zero@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 1, 3));

            // when
            int updated = repository.debitPackInterview(user.getId());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPackInterviewsLeft()).isZero();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("DebitPackTraining")
    class DebitPackTraining {

        @Test
        @DisplayName("Списывает pack-остаток независимо от плана и истёкшего срока")
        void debitsRegardlessOfExpiredPlan() {
            // given — PRO с истёкшим plan_expires_at: списание пакета не зависит от активности плана
            var user = em.persistAndFlush(aUser("billing-debit-pack-training-expired@example.com"));
            em.persistAndFlush(aProAccount(user.getId(), Instant.now().minusSeconds(3600), 0, 0, 5, 2));

            // when
            int updated = repository.debitPackTraining(user.getId());

            // then
            assertThat(updated).isEqualTo(1);
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPackTrainingsLeft()).isEqualTo(1);
        }

        @Test
        @DisplayName("Возвращает 0 и не меняет остаток при нулевом pack_trainings_left")
        void returnsZeroWhenNoPackTrainingsLeft() {
            // given
            var user = em.persistAndFlush(aUser("billing-debit-pack-training-zero@example.com"));
            em.persistAndFlush(aFreeAccount(user.getId(), 1, 3));

            // when
            int updated = repository.debitPackTraining(user.getId());

            // then
            assertThat(updated).isZero();
            em.clear();
            var saved = repository.findById(user.getId()).orElseThrow();
            assertThat(saved.getPackTrainingsLeft()).isZero();
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Constraints")
    class Constraints {

        @Test
        @DisplayName("chk_account_plan: недопустимое значение plan бросает исключение при вставке")
        void throwsOnInvalidPlanValue() {
            // given — валидный user_id, chk_account_paid_plan_expires обойдён датой истечения,
            // чтобы упасть именно на chk_account_plan, а не на другом констрейнте
            var user = em.persistAndFlush(aUser("billing-constraint-invalid-plan@example.com"));

            // when / then
            assertThatThrownBy(() -> em.getEntityManager()
                    .createNativeQuery("""
                            INSERT INTO billing.account
                                (user_id, plan, plan_expires_at, plan_interviews_left, plan_trainings_left,
                                 pack_interviews_left, pack_trainings_left)
                            VALUES (:userId, 'BAD_PLAN', now() + interval '1 hour', 1, 3, 0, 0)
                            """)
                    .setParameter("userId", user.getId())
                    .executeUpdate())
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("chk_account_left_non_negative: отрицательный остаток бросает исключение при flush")
        void throwsOnNegativeLeft() {
            // given
            var user = em.persistAndFlush(aUser("billing-constraint-negative@example.com"));
            var bad = aFreeAccount(user.getId(), -1, 3);

            // when / then
            assertThatThrownBy(() -> em.persistAndFlush(bad))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("chk_account_paid_plan_expires: платный план без даты истечения бросает исключение при flush")
        void throwsOnPaidPlanWithoutExpiry() {
            // given
            var user = em.persistAndFlush(aUser("billing-constraint-no-expiry@example.com"));
            var bad = aProAccount(user.getId(), null, 5, 5, 0, 0);

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
            em.persistAndFlush(aFreeAccount(userId, 1, 3));

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
