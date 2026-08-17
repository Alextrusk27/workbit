package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.billing.repository.UsageEventRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuotaServiceTest")
class QuotaServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String INTERVIEW_LABEL = "Интервью — Java-разработчик";
    private static final String TRAINING_LABEL = "Тренировка — Java, Уверенный";
    private static final String CREDIT_LABEL = "Тариф «Про» на 30 дней";

    @Mock
    BillingAccountRepository billingAccountRepository;
    @Mock
    UsageEventRepository usageEventRepository;

    @InjectMocks
    QuotaService quotaService;

    private static BillingAccount anAccount(BillingAccount.Plan plan, Instant planExpiresAt,
                                            int planInterviewsLeft, int planTrainingsLeft) {
        return BillingAccount.builder()
                .userId(USER_ID)
                .plan(plan)
                .planExpiresAt(planExpiresAt)
                .planInterviewsLeft(planInterviewsLeft)
                .planTrainingsLeft(planTrainingsLeft)
                .build();
    }

    @Nested
    @DisplayName("GetQuota")
    class GetQuota {

        @Test
        @DisplayName("Строки в БД нет - виртуальный дефолт FREE (1 интервью, 3 тренировки)")
        void returnsFreeDefaultWhenNoRow() {
            // given
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.FREE, null, 1, 3));
        }

        @Test
        @DisplayName("Активный PRO - остатки возвращаются из строки как есть")
        void returnsAccountAsIsWhenPlanActive() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, future, 7, 15);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.PRO, future, 7, 15));
        }

        @Test
        @DisplayName("Истёкший PRO - читается как FREE с нулевыми остатками, planExpiresAt null")
        void readsExpiredPlanAsFree() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.FREE, null, 0, 0));
        }

        @Test
        @DisplayName("Активный MAX - planTrainingsLeft null (безлимит тренировок), planInterviewsLeft из строки")
        void returnsNullTrainingsLeftOnActiveMax() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            QuotaResponse result = quotaService.getQuota(USER_ID);

            // then
            assertThat(result).isEqualTo(new QuotaResponse(BillingAccount.Plan.MAX, future, 20, null));
        }
    }

    @Nested
    @DisplayName("CheckInterviewAvailable")
    class CheckInterviewAvailable {

        @Test
        @DisplayName("Остаток интервью равен нулю - PaymentRequiredException")
        void throwsWhenNoInterviewsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 0, 3);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkInterviewAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
        }

        @Test
        @DisplayName("Истёкший PRO - PaymentRequiredException, несписанный остаток не учитывается")
        void throwsWhenPlanExpired() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkInterviewAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
        }

        @Test
        @DisplayName("Остаток интервью больше нуля - проходит без исключения")
        void passesWhenInterviewsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 1, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkInterviewAvailable(USER_ID);
        }
    }

    @Nested
    @DisplayName("CheckTrainingAvailable")
    class CheckTrainingAvailable {

        @Test
        @DisplayName("Остаток тренировок равен нулю - PaymentRequiredException")
        void throwsWhenNoTrainingsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 1, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkTrainingAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
        }

        @Test
        @DisplayName("Истёкший PRO - PaymentRequiredException, несписанный остаток не учитывается")
        void throwsWhenPlanExpired() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> quotaService.checkTrainingAvailable(USER_ID))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
        }

        @Test
        @DisplayName("Остаток тренировок больше нуля - проходит без исключения")
        void passesWhenTrainingsLeft() {
            // given
            BillingAccount account = anAccount(BillingAccount.Plan.FREE, null, 0, 3);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkTrainingAvailable(USER_ID);
        }

        @Test
        @DisplayName("Активный MAX - безлимит тренировок (planTrainingsLeft null) - проходит без исключения")
        void passesWhenPlanIsActiveMaxUnlimited() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 0);
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
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10);
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
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, future, 5, 10);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkPaidPlan(USER_ID);
        }

        @Test
        @DisplayName("Активный MAX - проходит без исключения")
        void passesWhenPlanIsActiveMax() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 40);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            quotaService.checkPaidPlan(USER_ID);
        }
    }

    @Nested
    @DisplayName("GetUsage")
    class GetUsage {

        @Test
        @DisplayName("Строки в БД нет - виртуальный дефолт FREE по обоим счётчикам (total = сетка FREE)")
        void returnsFreeDefaultCountersWhenNoRow() {
            // given
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of());

            // when
            UsageResponse result = quotaService.getUsage(USER_ID);

            // then
            assertThat(result.interviews()).isEqualTo(new UsageResponse.UsageCounter(1, 1));
            assertThat(result.trainings()).isEqualTo(new UsageResponse.UsageCounter(3, 3));
            assertThat(result.events()).isEmpty();
        }

        @Test
        @DisplayName("Активный тариф PRO - left из аккаунта, total из сетки тарифа")
        void returnsAccountCountersWhenPlanActive() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, future, 7, 15);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of());

            // when
            UsageResponse result = quotaService.getUsage(USER_ID);

            // then
            assertThat(result.interviews())
                    .isEqualTo(new UsageResponse.UsageCounter(7, BillingAccount.Plan.PRO.getInterviews()));
            assertThat(result.trainings())
                    .isEqualTo(new UsageResponse.UsageCounter(15, BillingAccount.Plan.PRO.getTrainings()));
        }

        @Test
        @DisplayName("Истёкший платный тариф - счётчики нулевые (0/0)")
        void returnsZeroedPlanCountersWhenPlanExpired() {
            // given
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.PRO, past, 5, 10);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of());

            // when
            UsageResponse result = quotaService.getUsage(USER_ID);

            // then
            assertThat(result.interviews()).isEqualTo(new UsageResponse.UsageCounter(0, 0));
            assertThat(result.trainings()).isEqualTo(new UsageResponse.UsageCounter(0, 0));
        }

        @Test
        @DisplayName("Активный тариф MAX - счётчик тренировок безлимитный (null/null), интервью - как обычно")
        void returnsUnlimitedTrainingsCounterOnActiveMax() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of());

            // when
            UsageResponse result = quotaService.getUsage(USER_ID);

            // then
            assertThat(result.interviews())
                    .isEqualTo(new UsageResponse.UsageCounter(20, BillingAccount.Plan.MAX.getInterviews()));
            assertThat(result.trainings()).isEqualTo(new UsageResponse.UsageCounter(null, null));
        }

        @Test
        @DisplayName("Каждое поле события переносится в UsageEventResponse как есть, порядок из репозитория (новые первыми) сохраняется")
        void mapsEventsPreservingOrderAndFields() {
            // given
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());
            Instant newerAt = Instant.now();
            Instant olderAt = newerAt.minusSeconds(60);
            UsageEvent newer = UsageEvent.builder()
                    .userId(USER_ID).at(newerAt).kind(UsageEvent.Kind.SPEND)
                    .target(UsageEvent.Target.INTERVIEW).delta(1).label(INTERVIEW_LABEL).build();
            UsageEvent older = UsageEvent.builder()
                    .userId(USER_ID).at(olderAt).kind(UsageEvent.Kind.SPEND)
                    .target(UsageEvent.Target.TRAINING).delta(1).label(TRAINING_LABEL).build();
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of(newer, older));

            // when
            UsageResponse result = quotaService.getUsage(USER_ID);

            // then
            assertThat(result.events()).containsExactly(
                    new UsageResponse.UsageEventResponse(
                            newerAt, UsageEvent.Kind.SPEND, UsageEvent.Target.INTERVIEW, 1, INTERVIEW_LABEL),
                    new UsageResponse.UsageEventResponse(
                            olderAt, UsageEvent.Kind.SPEND, UsageEvent.Target.TRAINING, 1, TRAINING_LABEL));
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
            quotaService.debitInterview(USER_ID, INTERVIEW_LABEL);

            // then
            InOrder inOrder = inOrder(billingAccountRepository);
            inOrder.verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            inOrder.verify(billingAccountRepository).debitPlanInterview(eq(USER_ID), any());
        }

        @Test
        @DisplayName("Успешное списание - сохраняет SPEND-событие с переданным label и target INTERVIEW")
        void savesSpendEventWithLabelAndTarget() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitInterview(USER_ID, INTERVIEW_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.SPEND);
            assertThat(saved.getTarget()).isEqualTo(UsageEvent.Target.INTERVIEW);
            assertThat(saved.getDelta()).isEqualTo(1);
            assertThat(saved.getLabel()).isEqualTo(INTERVIEW_LABEL);
        }

        @Test
        @DisplayName("Списание вернуло 0 - PaymentRequiredException, событие не сохраняется")
        void throwsWhenPlanExhausted() {
            // given
            when(billingAccountRepository.debitPlanInterview(eq(USER_ID), any())).thenReturn(0);

            // when / then
            assertThatThrownBy(() -> quotaService.debitInterview(USER_ID, INTERVIEW_LABEL))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Interview quota exhausted");
            verify(usageEventRepository, never()).save(any());
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
            quotaService.debitTraining(USER_ID, TRAINING_LABEL);

            // then
            InOrder inOrder = inOrder(billingAccountRepository);
            inOrder.verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            inOrder.verify(billingAccountRepository).debitPlanTraining(eq(USER_ID), any());
        }

        @Test
        @DisplayName("Успешное списание - сохраняет SPEND-событие с переданным label и target TRAINING")
        void savesSpendEventWithLabelAndTarget() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(1);

            // when
            quotaService.debitTraining(USER_ID, TRAINING_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.SPEND);
            assertThat(saved.getTarget()).isEqualTo(UsageEvent.Target.TRAINING);
            assertThat(saved.getDelta()).isEqualTo(1);
            assertThat(saved.getLabel()).isEqualTo(TRAINING_LABEL);
        }

        @Test
        @DisplayName("Списание вернуло 0 - PaymentRequiredException, событие не сохраняется")
        void throwsWhenPlanExhausted() {
            // given
            when(billingAccountRepository.debitPlanTraining(eq(USER_ID), any())).thenReturn(0);

            // when / then
            assertThatThrownBy(() -> quotaService.debitTraining(USER_ID, TRAINING_LABEL))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
            verify(usageEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Активный MAX (безлимит) - SPEND-событие пишется, но debitPlanTraining не вызывается")
        void writesSpendEventWithoutDebitingOnUnlimitedMax() {
            // given
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(BillingAccount.Plan.MAX, future, 20, 0);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            quotaService.debitTraining(USER_ID, TRAINING_LABEL);

            // then
            verify(billingAccountRepository, never()).debitPlanTraining(any(), any());
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.SPEND);
            assertThat(saved.getTarget()).isEqualTo(UsageEvent.Target.TRAINING);
            assertThat(saved.getDelta()).isEqualTo(1);
            assertThat(saved.getLabel()).isEqualTo(TRAINING_LABEL);
        }
    }

    @Nested
    @DisplayName("CreditPlan")
    class CreditPlan {

        @Test
        @DisplayName("insertIfAbsent вызывается, billingAccountRepository.creditPlan - с планом, сеткой квот и now")
        void callsInsertIfAbsentAndCreditsAccount() {
            // when
            quotaService.creditPlan(USER_ID, BillingAccount.Plan.PRO, CREDIT_LABEL);

            // then
            verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            verify(billingAccountRepository).creditPlan(eq(USER_ID), eq(BillingAccount.Plan.PRO.name()),
                    eq(BillingAccount.Plan.PRO.getInterviews()), eq(BillingAccount.Plan.PRO.getTrainings()),
                    any(Instant.class));
        }

        @Test
        @DisplayName("Сохраняет два CREDIT-события с одинаковым at и label - INTERVIEW и TRAINING с дельтой из сетки плана")
        void savesTwoCreditEventsWithSameAtAndLabel() {
            // when
            quotaService.creditPlan(USER_ID, BillingAccount.Plan.PRO, CREDIT_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository, times(2)).save(captor.capture());
            List<UsageEvent> saved = captor.getAllValues();

            UsageEvent interviewEvent = saved.get(0);
            UsageEvent trainingEvent = saved.get(1);

            assertThat(interviewEvent.getUserId()).isEqualTo(USER_ID);
            assertThat(interviewEvent.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(interviewEvent.getTarget()).isEqualTo(UsageEvent.Target.INTERVIEW);
            assertThat(interviewEvent.getDelta()).isEqualTo(BillingAccount.Plan.PRO.getInterviews());
            assertThat(interviewEvent.getLabel()).isEqualTo(CREDIT_LABEL);

            assertThat(trainingEvent.getUserId()).isEqualTo(USER_ID);
            assertThat(trainingEvent.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(trainingEvent.getTarget()).isEqualTo(UsageEvent.Target.TRAINING);
            assertThat(trainingEvent.getDelta()).isEqualTo(BillingAccount.Plan.PRO.getTrainings());
            assertThat(trainingEvent.getLabel()).isEqualTo(CREDIT_LABEL);

            assertThat(interviewEvent.getAt()).isEqualTo(trainingEvent.getAt());
        }

        @Test
        @DisplayName("План с безлимитными тренировками (MAX) - пишет только CREDIT-событие INTERVIEW, TRAINING не пишет")
        void savesOnlyInterviewCreditEventForUnlimitedTrainingsPlan() {
            // when
            quotaService.creditPlan(USER_ID, BillingAccount.Plan.MAX, CREDIT_LABEL);

            // then
            verify(billingAccountRepository).creditPlan(eq(USER_ID), eq(BillingAccount.Plan.MAX.name()),
                    eq(BillingAccount.Plan.MAX.getInterviews()), eq(BillingAccount.Plan.MAX.getTrainings()),
                    any(Instant.class));

            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository, times(1)).save(captor.capture());
            UsageEvent interviewEvent = captor.getValue();

            assertThat(interviewEvent.getUserId()).isEqualTo(USER_ID);
            assertThat(interviewEvent.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(interviewEvent.getTarget()).isEqualTo(UsageEvent.Target.INTERVIEW);
            assertThat(interviewEvent.getDelta()).isEqualTo(BillingAccount.Plan.MAX.getInterviews());
            assertThat(interviewEvent.getLabel()).isEqualTo(CREDIT_LABEL);
        }
    }

    @Nested
    @DisplayName("CreditInterviews")
    class CreditInterviews {

        private static final int COUNT = 2;
        private static final String GIFT_LABEL = GiftService.PROMO_LABEL;

        @Test
        @DisplayName("insertIfAbsent вызывается, billingAccountRepository.creditInterviews - с переданным count")
        void callsInsertIfAbsentAndCreditsInterviews() {
            // when
            quotaService.creditInterviews(USER_ID, COUNT, GIFT_LABEL);

            // then
            verify(billingAccountRepository).insertIfAbsent(
                    USER_ID, BillingAccount.Plan.FREE.getInterviews(), BillingAccount.Plan.FREE.getTrainings());
            verify(billingAccountRepository).creditInterviews(USER_ID, COUNT);
        }

        @Test
        @DisplayName("Сохраняет CREDIT-событие INTERVIEW с delta = count и переданным label")
        void savesCreditEventWithCountAndLabel() {
            // when
            quotaService.creditInterviews(USER_ID, COUNT, GIFT_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(saved.getTarget()).isEqualTo(UsageEvent.Target.INTERVIEW);
            assertThat(saved.getDelta()).isEqualTo(COUNT);
            assertThat(saved.getLabel()).isEqualTo(GIFT_LABEL);
        }
    }
}
