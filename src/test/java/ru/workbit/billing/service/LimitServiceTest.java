package ru.workbit.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.dto.BalanceResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.billing.repository.UsageEventRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

@ExtendWith(MockitoExtension.class)
@DisplayName("LimitServiceTest")
class LimitServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String INTERVIEW_LABEL = "Интервью — Java-разработчик";
    private static final int TOPUP_LIMITS = 50;
    private static final String TOPUP_LABEL = TopUpPricing.label(TOPUP_LIMITS);

    @Mock
    BillingAccountRepository billingAccountRepository;
    @Mock
    UsageEventRepository usageEventRepository;

    @InjectMocks
    LimitService limitService;

    private static BillingAccount anAccount(int limits, Instant expiresAt, Instant paidAt) {
        return BillingAccount.builder()
                .userId(USER_ID)
                .limits(limits)
                .limitsExpireAt(expiresAt)
                .paidAt(paidAt)
                .build();
    }

    private void stubExistingUser() {
        when(billingAccountRepository.insertIfAbsent(eq(USER_ID), eq(LimitService.WELCOME_LIMITS), any()))
                .thenReturn(0);
    }

    private void stubNewUser() {
        when(billingAccountRepository.insertIfAbsent(eq(USER_ID), eq(LimitService.WELCOME_LIMITS), any()))
                .thenReturn(1);
    }

    @Nested
    @DisplayName("GetBalance")
    class GetBalance {

        @Test
        @DisplayName("Активный баланс - остаток и срок возвращаются из аккаунта как есть")
        void returnsAccountAsIsWhenActive() {
            // given
            stubExistingUser();
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(15, future, Instant.now());
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            BalanceResponse result = limitService.getBalance(USER_ID);

            // then
            assertThat(result).isEqualTo(new BalanceResponse(15, future, true));
            verify(usageEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Просроченный срок - остаток и срок обнуляются, paid сохраняется")
        void zeroesOutWhenExpired() {
            // given
            stubExistingUser();
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(15, past, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            BalanceResponse result = limitService.getBalance(USER_ID);

            // then
            assertThat(result).isEqualTo(new BalanceResponse(0, null, false));
        }

        @Test
        @DisplayName("Аккаунт после миграции с истёкшим тарифом (0 лимитов, срок null) - остаток нулевой, paid сохраняется")
        void zeroesOutForMigratedExpiredPlan() {
            // given
            stubExistingUser();
            BillingAccount account = anAccount(0, null, Instant.now());
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            BalanceResponse result = limitService.getBalance(USER_ID);

            // then
            assertThat(result).isEqualTo(new BalanceResponse(0, null, true));
        }

        @Test
        @DisplayName("Новый пользователь (insertIfAbsent вернул 1) - пишет CREDIT WELCOME на 20 лимитов")
        void writesWelcomeEventForNewUser() {
            // given
            stubNewUser();
            Instant future = Instant.now().plus(90, ChronoUnit.DAYS);
            BillingAccount account = anAccount(20, future, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            limitService.getBalance(USER_ID);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(saved.getOperation()).isEqualTo(UsageEvent.Operation.WELCOME);
            assertThat(saved.getDelta()).isEqualTo(LimitService.WELCOME_LIMITS);
            assertThat(saved.getLabel()).isEqualTo(LimitService.WELCOME_LABEL);
        }
    }

    @Nested
    @DisplayName("GetUsage")
    class GetUsage {

        @Test
        @DisplayName("Отдаёт баланс и события с полем operation, порядок из репозитория сохраняется")
        void mapsBalanceAndEvents() {
            // given
            stubExistingUser();
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(15, future, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));
            Instant at = Instant.now();
            UsageEvent event = UsageEvent.builder()
                    .userId(USER_ID).at(at).kind(UsageEvent.Kind.SPEND)
                    .operation(UsageEvent.Operation.INTERVIEW).delta(20).label(INTERVIEW_LABEL).build();
            when(usageEventRepository.findAllByUserIdOrderByAtDesc(USER_ID)).thenReturn(List.of(event));

            // when
            UsageResponse result = limitService.getUsage(USER_ID);

            // then
            assertThat(result.limits()).isEqualTo(15);
            assertThat(result.expiresAt()).isEqualTo(future);
            assertThat(result.paid()).isFalse();
            assertThat(result.events()).containsExactly(new UsageResponse.UsageEventResponse(
                    at, UsageEvent.Kind.SPEND, UsageEvent.Operation.INTERVIEW, 20, INTERVIEW_LABEL));
        }
    }

    @Nested
    @DisplayName("Check")
    class Check {

        @Test
        @DisplayName("Достаточно лимитов - проходит без исключения")
        void passesWhenEnoughLimits() {
            // given
            stubExistingUser();
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(20, future, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            limitService.check(USER_ID, UsageEvent.Operation.INTERVIEW);
        }

        @Test
        @DisplayName("Недостаточно лимитов - PaymentRequiredException")
        void throwsWhenNotEnoughLimits() {
            // given
            stubExistingUser();
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(5, future, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> limitService.check(USER_ID, UsageEvent.Operation.INTERVIEW))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Not enough limits");
        }

        @Test
        @DisplayName("Срок лимитов истёк - эффективный остаток нулевой - PaymentRequiredException")
        void throwsWhenExpired() {
            // given
            stubExistingUser();
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(30, past, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> limitService.check(USER_ID, UsageEvent.Operation.INTERVIEW))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Not enough limits");
        }

        @Test
        @DisplayName("Пользователь без строки - приветственных 20 лимитов хватает на интервью")
        void passesForNewUserOnWelcomeLimits() {
            // given
            stubNewUser();
            Instant future = Instant.now().plus(90, ChronoUnit.DAYS);
            BillingAccount account = anAccount(20, future, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when
            limitService.check(USER_ID, UsageEvent.Operation.INTERVIEW);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(UsageEvent.Operation.WELCOME);
        }
    }

    @Nested
    @DisplayName("Debit")
    class Debit {

        @Test
        @DisplayName("Успешное списание - сохраняет SPEND-событие с ценой операции и меткой")
        void savesSpendEventWithLimitsAndLabel() {
            // given
            stubExistingUser();
            when(billingAccountRepository.debit(eq(USER_ID), eq(UsageEvent.Operation.INTERVIEW.getCost()), any()))
                    .thenReturn(1);

            // when
            limitService.debit(USER_ID, UsageEvent.Operation.INTERVIEW, INTERVIEW_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.SPEND);
            assertThat(saved.getOperation()).isEqualTo(UsageEvent.Operation.INTERVIEW);
            assertThat(saved.getDelta()).isEqualTo(UsageEvent.Operation.INTERVIEW.getCost());
            assertThat(saved.getLabel()).isEqualTo(INTERVIEW_LABEL);
        }

        @Test
        @DisplayName("Репозиторий вернул 0 (лимитов не хватило) - PaymentRequiredException, событие не сохраняется")
        void throwsWhenRepositoryDebitsZero() {
            // given
            stubExistingUser();
            when(billingAccountRepository.debit(eq(USER_ID), eq(UsageEvent.Operation.INTERVIEW.getCost()), any()))
                    .thenReturn(0);

            // when / then
            assertThatThrownBy(() -> limitService.debit(USER_ID, UsageEvent.Operation.INTERVIEW, INTERVIEW_LABEL))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Not enough limits");
            verify(usageEventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("CreditTopUp")
    class CreditTopUp {

        @Test
        @DisplayName("Активный баланс - пишет только TOPUP, EXPIRE не пишет")
        void writesOnlyTopUpWhenActive() {
            // given
            stubExistingUser();
            Instant future = Instant.now().plus(10, ChronoUnit.DAYS);
            BillingAccount account = anAccount(15, future, null);
            when(billingAccountRepository.findForUpdate(USER_ID)).thenReturn(Optional.of(account));

            // when
            limitService.creditTopUp(USER_ID, TOPUP_LIMITS, TOPUP_LABEL);

            // then
            verify(billingAccountRepository).creditTopUp(eq(USER_ID), eq(TOPUP_LIMITS), any());
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            UsageEvent saved = captor.getValue();
            assertThat(saved.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(saved.getOperation()).isEqualTo(UsageEvent.Operation.TOPUP);
            assertThat(saved.getDelta()).isEqualTo(TOPUP_LIMITS);
            assertThat(saved.getLabel()).isEqualTo(TOPUP_LABEL);
        }

        @Test
        @DisplayName("Просроченный баланс с ненулевым остатком - сначала EXPIRE на старый остаток, потом TOPUP")
        void writesExpireThenTopUpWhenExpiredWithLimits() {
            // given
            stubExistingUser();
            Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
            BillingAccount account = anAccount(30, past, null);
            when(billingAccountRepository.findForUpdate(USER_ID)).thenReturn(Optional.of(account));

            // when
            limitService.creditTopUp(USER_ID, TOPUP_LIMITS, TOPUP_LABEL);

            // then
            InOrder inOrderCheck = inOrder(usageEventRepository, billingAccountRepository);
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            inOrderCheck.verify(usageEventRepository).save(captor.capture());
            inOrderCheck.verify(billingAccountRepository)
                    .creditTopUp(eq(USER_ID), eq(TOPUP_LIMITS), any());
            inOrderCheck.verify(usageEventRepository).save(captor.capture());

            List<UsageEvent> saved = captor.getAllValues();
            assertThat(saved).hasSize(2);
            UsageEvent expireEvent = saved.get(0);
            assertThat(expireEvent.getKind()).isEqualTo(UsageEvent.Kind.SPEND);
            assertThat(expireEvent.getOperation()).isEqualTo(UsageEvent.Operation.EXPIRE);
            assertThat(expireEvent.getDelta()).isEqualTo(30);
            assertThat(expireEvent.getLabel()).isEqualTo(LimitService.EXPIRE_LABEL);

            UsageEvent topUpEvent = saved.get(1);
            assertThat(topUpEvent.getKind()).isEqualTo(UsageEvent.Kind.CREDIT);
            assertThat(topUpEvent.getOperation()).isEqualTo(UsageEvent.Operation.TOPUP);
            assertThat(topUpEvent.getDelta()).isEqualTo(TOPUP_LIMITS);
            assertThat(topUpEvent.getLabel()).isEqualTo(TOPUP_LABEL);
        }

        @Test
        @DisplayName("Аккаунт после миграции с истёкшим тарифом (0 лимитов, срок null) - EXPIRE не пишется")
        void doesNotWriteExpireForMigratedZeroBalance() {
            // given
            stubExistingUser();
            BillingAccount account = anAccount(0, null, null);
            when(billingAccountRepository.findForUpdate(USER_ID)).thenReturn(Optional.of(account));

            // when
            limitService.creditTopUp(USER_ID, TOPUP_LIMITS, TOPUP_LABEL);

            // then
            ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
            verify(usageEventRepository).save(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(UsageEvent.Operation.TOPUP);
        }
    }

    @Nested
    @DisplayName("RequirePaid")
    class RequirePaid {

        @Test
        @DisplayName("Без покупок - ForbiddenException")
        void throwsWhenNeverPaid() {
            // given
            stubExistingUser();
            BillingAccount account = anAccount(0, null, null);
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            assertThatThrownBy(() -> limitService.requirePaid(USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Purchase required");
        }

        @Test
        @DisplayName("Была покупка - проходит без исключения")
        void passesWhenPaid() {
            // given
            stubExistingUser();
            BillingAccount account = anAccount(15, Instant.now().plus(10, ChronoUnit.DAYS), Instant.now());
            when(billingAccountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

            // when / then
            limitService.requirePaid(USER_ID);
        }
    }
}
