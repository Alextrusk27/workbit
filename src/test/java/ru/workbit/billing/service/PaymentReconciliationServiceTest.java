package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.model.Payment;
import ru.workbit.billing.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReconciliationServiceTest")
class PaymentReconciliationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = Payment.Product.PLAN_PRO.getPrice();

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentProvider paymentProvider;
    @Mock
    PaymentService paymentService;

    @InjectMocks
    PaymentReconciliationService reconciliationService;

    private static Payment aPayment(int invId) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .invId(invId)
                .userId(USER_ID)
                .product(Payment.Product.PLAN_PRO)
                .amount(AMOUNT)
                .status(Payment.Status.PENDING)
                .build();
    }

    @Nested
    @DisplayName("ReconcilePending")
    class ReconcilePending {

        @Test
        @DisplayName("Выбирает PENDING-платежи с окном created между now-1день и now-30секунд")
        void queriesPendingPaymentsWithinAgeWindow() {
            // given
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of());
            Instant before = Instant.now();

            // when
            reconciliationService.reconcilePending();

            // then
            Instant after = Instant.now();
            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(paymentRepository).findByStatusAndCreatedBetween(
                    eq(Payment.Status.PENDING), fromCaptor.capture(), toCaptor.capture());

            assertThat(fromCaptor.getValue())
                    .isCloseTo(before.minus(Duration.ofDays(1)), within(5, ChronoUnit.SECONDS))
                    .isCloseTo(after.minus(Duration.ofDays(1)), within(5, ChronoUnit.SECONDS));
            assertThat(toCaptor.getValue())
                    .isCloseTo(before.minus(Duration.ofSeconds(30)), within(5, ChronoUnit.SECONDS))
                    .isCloseTo(after.minus(Duration.ofSeconds(30)), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Пустая выборка — провайдер и paymentService не дёргаются")
        void doesNothingWhenNoPendingPayments() {
            // given
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of());

            // when
            reconciliationService.reconcilePending();

            // then
            verifyNoInteractions(paymentProvider, paymentService);
        }

        @Test
        @DisplayName("isPaid=true — confirmPaid вызван для платежа")
        void confirmsPaidPayment() {
            // given
            Payment payment = aPayment(42);
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of(payment));
            when(paymentProvider.isPaid(payment)).thenReturn(true);
            when(paymentService.confirmPaid(payment)).thenReturn(true);

            // when
            reconciliationService.reconcilePending();

            // then
            verify(paymentService).confirmPaid(payment);
        }

        @Test
        @DisplayName("isPaid=false — confirmPaid не вызывается")
        void skipsUnpaidPayment() {
            // given
            Payment payment = aPayment(42);
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of(payment));
            when(paymentProvider.isPaid(payment)).thenReturn(false);

            // when
            reconciliationService.reconcilePending();

            // then
            verify(paymentService, never()).confirmPaid(any());
        }

        @Test
        @DisplayName("Несколько платежей — все проверены isPaid, confirmPaid только по оплаченным")
        void processesAllPaymentsAndConfirmsOnlyPaidOnes() {
            // given
            Payment paidPayment = aPayment(1);
            Payment unpaidPayment = aPayment(2);
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of(paidPayment, unpaidPayment));
            when(paymentProvider.isPaid(paidPayment)).thenReturn(true);
            when(paymentProvider.isPaid(unpaidPayment)).thenReturn(false);
            when(paymentService.confirmPaid(paidPayment)).thenReturn(true);

            // when
            reconciliationService.reconcilePending();

            // then
            verify(paymentProvider).isPaid(paidPayment);
            verify(paymentProvider).isPaid(unpaidPayment);
            verify(paymentService, times(1)).confirmPaid(any());
            verify(paymentService).confirmPaid(paidPayment);
        }

        @Test
        @DisplayName("confirmPaid вернул false (уже подтверждён конкурентно) — не падает, остальные платежи обрабатываются")
        void continuesProcessingWhenConfirmPaidReturnsFalse() {
            // given
            Payment alreadyConfirmedPayment = aPayment(1);
            Payment paidPayment = aPayment(2);
            when(paymentRepository.findByStatusAndCreatedBetween(eq(Payment.Status.PENDING), any(), any()))
                    .thenReturn(List.of(alreadyConfirmedPayment, paidPayment));
            when(paymentProvider.isPaid(alreadyConfirmedPayment)).thenReturn(true);
            when(paymentProvider.isPaid(paidPayment)).thenReturn(true);
            when(paymentService.confirmPaid(alreadyConfirmedPayment)).thenReturn(false);
            when(paymentService.confirmPaid(paidPayment)).thenReturn(true);

            // when / then
            reconciliationService.reconcilePending();

            verify(paymentService).confirmPaid(alreadyConfirmedPayment);
            verify(paymentService).confirmPaid(paidPayment);
        }
    }
}
