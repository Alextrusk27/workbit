package ru.workbit.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.auth.repository.UserJPARepository;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.model.Payment;
import ru.workbit.billing.repository.PaymentRepository;
import ru.workbit.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceTest")
class PaymentServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final int INV_ID = 42;
    private static final int LIMITS = 50;
    private static final BigDecimal AMOUNT = new BigDecimal("750.00");
    private static final String LABEL = "Пополнение на 50 лимитов";
    private static final String EMAIL = "user@example.com";
    private static final String PAYMENT_URL = "https://auth.robokassa.ru/pay?x=1";
    private static final String NOTIFICATION_RESPONSE = "OK42";
    private static final Map<String, String> PARAMS =
            Map.of("OutSum", "750.00", "InvId", "42", "SignatureValue", "sig");

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentProvider paymentProvider;
    @Mock
    LimitService limitService;
    @Mock
    UserJPARepository userRepository;

    @InjectMocks
    PaymentService paymentService;

    private static Payment aPayment(Payment.Status status) {
        return Payment.builder()
                .id(PAYMENT_ID)
                .invId(INV_ID)
                .userId(USER_ID)
                .limits(LIMITS)
                .amount(AMOUNT)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("Берёт nextInvId, сохраняет PENDING с суммой по сетке TopUpPricing, возвращает paymentId и URL от провайдера")
        void createsPendingPaymentAndReturnsUrlFromProvider() {
            // given
            when(paymentRepository.nextInvId()).thenReturn(INV_ID);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment saved = inv.getArgument(0);
                saved.setId(PAYMENT_ID);
                return saved;
            });
            when(paymentProvider.paymentUrl(any(Payment.class), eq(EMAIL))).thenReturn(PAYMENT_URL);

            // when
            PaymentCreateResponse response = paymentService.create(USER_ID, LIMITS, EMAIL);

            // then
            assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(response.paymentUrl()).isEqualTo(PAYMENT_URL);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentProvider).paymentUrl(captor.capture(), eq(EMAIL));
            Payment passedToProvider = captor.getValue();
            assertThat(passedToProvider.getId()).isEqualTo(PAYMENT_ID);
            assertThat(passedToProvider.getInvId()).isEqualTo(INV_ID);
            assertThat(passedToProvider.getUserId()).isEqualTo(USER_ID);
            assertThat(passedToProvider.getLimits()).isEqualTo(LIMITS);
            assertThat(passedToProvider.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(passedToProvider.getStatus()).isEqualTo(Payment.Status.PENDING);
        }

        @Test
        @DisplayName("Число лимитов ниже минимума — IllegalArgumentException, репозиторий и провайдер не трогаются")
        void throwsBelowMinimum() {
            // when / then
            assertThatThrownBy(() -> paymentService.create(USER_ID, 0, EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(paymentRepository, paymentProvider);
        }

        @Test
        @DisplayName("Число лимитов не кратно шагу — IllegalArgumentException, репозиторий и провайдер не трогаются")
        void throwsWhenNotMultipleOfStep() {
            // when / then
            assertThatThrownBy(() -> paymentService.create(USER_ID, 55, EMAIL))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(paymentRepository, paymentProvider);
        }
    }

    @Nested
    @DisplayName("Confirm")
    class Confirm {

        @Test
        @DisplayName("parseNotification кинул IllegalArgumentException — пробрасывается, репозиторий не трогается")
        void propagatesExceptionFromParseNotification() {
            // given
            when(paymentProvider.parseNotification(PARAMS))
                    .thenThrow(new IllegalArgumentException("Invalid signature"));

            // when / then
            assertThatThrownBy(() -> paymentService.confirm(PARAMS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid signature");
            verifyNoInteractions(paymentRepository, limitService);
        }

        @Test
        @DisplayName("Неизвестный invId — IllegalArgumentException, markPaid не вызывается")
        void throwsOnUnknownInvId() {
            // given
            when(paymentProvider.parseNotification(PARAMS))
                    .thenReturn(new PaymentProvider.Notification(INV_ID, AMOUNT));
            when(paymentRepository.findByInvId(INV_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> paymentService.confirm(PARAMS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown payment");
            verify(paymentRepository, never()).markPaid(any(), any());
            verifyNoInteractions(limitService);
        }

        @Test
        @DisplayName("Сумма не сходится с amount платежа — IllegalArgumentException, markPaid не вызывается")
        void throwsOnAmountMismatch() {
            // given
            when(paymentProvider.parseNotification(PARAMS))
                    .thenReturn(new PaymentProvider.Notification(INV_ID, new BigDecimal("999.00")));
            Payment payment = aPayment(Payment.Status.PENDING);
            when(paymentRepository.findByInvId(INV_ID)).thenReturn(Optional.of(payment));

            // when / then
            assertThatThrownBy(() -> paymentService.confirm(PARAMS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount mismatch");
            verify(paymentRepository, never()).markPaid(any(), any());
            verifyNoInteractions(limitService);
        }

        @Test
        @DisplayName("Happy path — markPaid вернул 1, creditTopUp вызван с числом лимитов платежа и меткой пополнения, возвращён ответ провайдера")
        void creditsTopUpOnFirstConfirmation() {
            // given
            when(paymentProvider.parseNotification(PARAMS))
                    .thenReturn(new PaymentProvider.Notification(INV_ID, AMOUNT));
            Payment payment = aPayment(Payment.Status.PENDING);
            when(paymentRepository.findByInvId(INV_ID)).thenReturn(Optional.of(payment));
            when(paymentRepository.markPaid(eq(PAYMENT_ID), any(Instant.class))).thenReturn(1);
            when(userRepository.existsById(USER_ID)).thenReturn(true);
            when(paymentProvider.notificationResponse(INV_ID)).thenReturn(NOTIFICATION_RESPONSE);

            // when
            String result = paymentService.confirm(PARAMS);

            // then
            assertThat(result).isEqualTo(NOTIFICATION_RESPONSE);
            verify(limitService).creditTopUp(USER_ID, LIMITS, LABEL);
        }

        @Test
        @DisplayName("Повторное уведомление — markPaid вернул 0, creditTopUp не вызывается, ответ провайдера всё равно возвращён")
        void isIdempotentOnRepeatedNotification() {
            // given
            when(paymentProvider.parseNotification(PARAMS))
                    .thenReturn(new PaymentProvider.Notification(INV_ID, AMOUNT));
            Payment payment = aPayment(Payment.Status.PAID);
            when(paymentRepository.findByInvId(INV_ID)).thenReturn(Optional.of(payment));
            when(paymentRepository.markPaid(eq(PAYMENT_ID), any(Instant.class))).thenReturn(0);
            when(paymentProvider.notificationResponse(INV_ID)).thenReturn(NOTIFICATION_RESPONSE);

            // when
            String result = paymentService.confirm(PARAMS);

            // then
            assertThat(result).isEqualTo(NOTIFICATION_RESPONSE);
            verifyNoInteractions(limitService);
        }
    }

    @Nested
    @DisplayName("ConfirmPaid")
    class ConfirmPaid {

        @Test
        @DisplayName("markPaid вернул 1 — creditTopUp вызван с числом лимитов платежа и меткой пополнения, возвращает true")
        void creditsTopUpAndReturnsTrueWhenMarkPaidSucceeds() {
            // given
            Payment payment = aPayment(Payment.Status.PENDING);
            when(paymentRepository.markPaid(eq(PAYMENT_ID), any(Instant.class))).thenReturn(1);
            when(userRepository.existsById(USER_ID)).thenReturn(true);

            // when
            boolean result = paymentService.confirmPaid(payment);

            // then
            assertThat(result).isTrue();
            verify(limitService).creditTopUp(USER_ID, LIMITS, LABEL);
        }

        @Test
        @DisplayName("Пользователь удалён — платёж помечен PAID, лимиты не начисляются, возвращает true")
        void marksPaidWithoutCreditingWhenUserDeleted() {
            // given
            Payment payment = aPayment(Payment.Status.PENDING);
            when(paymentRepository.markPaid(eq(PAYMENT_ID), any(Instant.class))).thenReturn(1);
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            // when
            boolean result = paymentService.confirmPaid(payment);

            // then
            assertThat(result).isTrue();
            verifyNoInteractions(limitService);
        }

        @Test
        @DisplayName("markPaid вернул 0 — creditTopUp не вызывается, возвращает false")
        void doesNotCreditAndReturnsFalseWhenMarkPaidFails() {
            // given
            Payment payment = aPayment(Payment.Status.PAID);
            when(paymentRepository.markPaid(eq(PAYMENT_ID), any(Instant.class))).thenReturn(0);

            // when
            boolean result = paymentService.confirmPaid(payment);

            // then
            assertThat(result).isFalse();
            verifyNoInteractions(limitService);
        }
    }

    @Nested
    @DisplayName("Get")
    class Get {

        @Test
        @DisplayName("Свой платёж — возвращает статус, число лимитов и сумму")
        void returnsStatusLimitsAndAmountForOwnPayment() {
            // given
            Payment payment = aPayment(Payment.Status.PAID);
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when
            PaymentStatusResponse response = paymentService.get(PAYMENT_ID, USER_ID);

            // then
            assertThat(response).isEqualTo(new PaymentStatusResponse(Payment.Status.PAID, LIMITS, AMOUNT));
        }

        @Test
        @DisplayName("Платёж не найден — NotFoundException")
        void throwsWhenPaymentNotFound() {
            // given
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> paymentService.get(PAYMENT_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Payment not found");
        }

        @Test
        @DisplayName("Чужой платёж (userId не совпал) — NotFoundException")
        void throwsWhenPaymentBelongsToAnotherUser() {
            // given
            Payment payment = aPayment(Payment.Status.PAID);
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            // when / then
            assertThatThrownBy(() -> paymentService.get(PAYMENT_ID, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Payment not found");
        }
    }
}
