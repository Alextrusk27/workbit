package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuotaServiceTest")
class QuotaServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    BillingAccountRepository billingAccountRepository;

    @InjectMocks
    QuotaService quotaService;

    private static BillingAccount anAccount(BillingAccount.Plan plan, Instant planExpiresAt,
                                            int planInterviewsLeft, int planTrainingsLeft,
                                            int packInterviewsLeft, int packTrainingsLeft) {
        return BillingAccount.builder()
                .userId(USER_ID)
                .plan(plan)
                .planExpiresAt(planExpiresAt)
                .planInterviewsLeft(planInterviewsLeft)
                .planTrainingsLeft(planTrainingsLeft)
                .packInterviewsLeft(packInterviewsLeft)
                .packTrainingsLeft(packTrainingsLeft)
                .build();
    }

    @Nested
    @DisplayName("GetQuota")
    class GetQuota {

        @Test
        @DisplayName("Строки в БД нет - виртуальный дефолт FREE (1 интервью, 3 тренировки), пакеты нулевые")
        void returnsFreeDefaultWhenNoRow() {
            // given
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.FREE, null, 1, 3, 0, 0));
        }

        @Test
        @DisplayName("Активный PRO - остатки возвращаются из строки как есть")
        void returnsAccountAsIsWhenPlanActive() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, future, 7, 15, 2, 4);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.PRO, future, 7, 15, 2, 4));
        }

        @Test
        @DisplayName("Истёкший PRO - читается как FREE с нулевыми подписочными остатками, planExpiresAt null, pack-остатки сохраняются")
        void readsExpiredPlanAsFreeKeepingPackLeft() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10, 3, 6);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.FREE, null, 0, 0, 3, 6));
        }
    }

    @Nested
    @DisplayName("CheckInterviewAvailable")
    class CheckInterviewAvailable {

        @Test
        @DisplayName("Суммарный остаток интервью (plan+pack) равен нулю - PaymentRequiredException")
        void throwsWhenNoInterviewsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 0, 3, 0, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkInterviewAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
        }

        @Test
        @DisplayName("Истёкший PRO с нулевым pack-остатком интервью - PaymentRequiredException")
        void throwsWhenExpiredPlanAndNoPackLeft() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10, 0, 6);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkInterviewAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
        }

        @Test
        @DisplayName("Подписочный остаток нулевой, но pack > 0 - проходит без исключения")
        void passesWhenPackLeftCoversZeroPlan() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 0, 3, 2, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkInterviewAvailable(USER_ID);
        }
    }

    @Nested
    @DisplayName("CheckTrainingAvailable")
    class CheckTrainingAvailable {

        @Test
        @DisplayName("Суммарный остаток тренировок (plan+pack) равен нулю - PaymentRequiredException")
        void throwsWhenNoTrainingsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 1, 0, 0, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkTrainingAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
        }

        @Test
        @DisplayName("Истёкший PRO с нулевым pack-остатком тренировок - PaymentRequiredException")
        void throwsWhenExpiredPlanAndNoPackLeft() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10, 6, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkTrainingAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
        }

        @Test
        @DisplayName("Подписочный остаток нулевой, но pack > 0 - проходит без исключения")
        void passesWhenPackLeftCoversZeroPlan() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 1, 0, 0, 3);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkTrainingAvailable(USER_ID);
        }
    }

    @Nested
    @DisplayName("CheckPaidPlan")
    class CheckPaidPlan {

        @Test
        @DisplayName("FREE (строки в БД нет) - ForbiddenException")
        void throwsWhenNoRowMeansFree() {
            // given
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> quotaService.checkPaidPlan(USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Paid plan required");
        }

        @Test
        @DisplayName("Истёкший PRO читается как FREE - ForbiddenException")
        void throwsWhenPlanExpired() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10, 0, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkPaidPlan(USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Paid plan required");
        }

        @Test
        @DisplayName("Активный PRO - проходит без исключения")
        void passesWhenPlanIsActivePro() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, future, 5, 10, 0, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkPaidPlan(USER_ID);
        }

        @Test
        @DisplayName("Активный MAX - проходит без исключения")
        void passesWhenPlanIsActiveMax() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 40, 0, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkPaidPlan(USER_ID);
        }
    }

    @Nested
    @DisplayName("DebitInterview")
    class DebitInterview {

        @Test
        @DisplayName("insertIfAbsent вызывается всегда, до списания с plan-остатка")
        void alwaysCallsInsertIfAbsentBeforeDebit() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitInterview(USER_ID);

            // then
            InOrder inOrder = inOrder(billingAccountRepository);
            inOrder.verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            inOrder.verify(billingAccountRepository).debitPlanInterview(eq(USER_ID), any());
        }

        @Test
        @DisplayName("Списание с plan-остатка успешно (>0) - pack не трогается")
        void debitsFromPlanFirst() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitInterview(USER_ID);

            // then
            verify(billingAccountRepository, never()).debitPackInterview(any());
        }

        @Test
        @DisplayName("Plan-остаток исчерпан (0) - списывается pack")
        void fallsBackToPackWhenPlanExhausted() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(0);
            when(billingAccountRepository.debitPackInterview(USER_ID)).thenReturn(1);

            // when
            quotaService.debitInterview(USER_ID);

            // then
            verify(billingAccountRepository).debitPackInterview(USER_ID);
        }

        @Test
        @DisplayName("И plan, и pack вернули 0 - PaymentRequiredException")
        void throwsWhenBothPlanAndPackExhausted() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(0);
            when(billingAccountRepository.debitPackInterview(USER_ID)).thenReturn(0);

            // when / then
            assertThatThrownBy(() -> quotaService.debitInterview(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
        }
    }

    @Nested
    @DisplayName("DebitTraining")
    class DebitTraining {

        @Test
        @DisplayName("insertIfAbsent вызывается всегда, до списания с plan-остатка")
        void alwaysCallsInsertIfAbsentBeforeDebit() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitTraining(USER_ID);

            // then
            InOrder inOrder = inOrder(billingAccountRepository);
            inOrder.verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            inOrder.verify(billingAccountRepository).debitPlanTraining(eq(USER_ID), any());
        }

        @Test
        @DisplayName("Списание с plan-остатка успешно (>0) - pack не трогается")
        void debitsFromPlanFirst() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitTraining(USER_ID);

            // then
            verify(billingAccountRepository, never()).debitPackTraining(any());
        }

        @Test
        @DisplayName("Plan-остаток исчерпан (0) - списывается pack")
        void fallsBackToPackWhenPlanExhausted() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(0);
            when(billingAccountRepository.debitPackTraining(USER_ID)).thenReturn(1);

            // when
            quotaService.debitTraining(USER_ID);

            // then
            verify(billingAccountRepository).debitPackTraining(USER_ID);
        }

        @Test
        @DisplayName("И plan, и pack вернули 0 - PaymentRequiredException")
        void throwsWhenBothPlanAndPackExhausted() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(0);
            when(billingAccountRepository.debitPackTraining(USER_ID)).thenReturn(0);

            // when / then
            assertThatThrownBy(() -> quotaService.debitTraining(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
        }
    }
}
