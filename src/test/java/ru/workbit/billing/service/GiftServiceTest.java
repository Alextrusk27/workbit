package ru.workbit.billing.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.model.Payment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("GiftServiceTest")
class GiftServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    QuotaService quotaService;

    @InjectMocks
    GiftService giftService;

    private static Payment aPayment(Payment.Product product) {
        return Payment.builder()
                .userId(USER_ID)
                .product(product)
                .build();
    }

    @Nested
    @DisplayName("GrantPromoGift")
    class GrantPromoGift {

        @Test
        @DisplayName("Оплата до дедлайна для PLAN_PRO — начисляет 2 интервью с меткой PROMO_LABEL")
        void creditsProGiftBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_PRO);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(quotaService).creditInterviews(USER_ID, Payment.Product.PLAN_PRO.getGiftInterviews(),
                    GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата до дедлайна для PLAN_MAX — начисляет 5 интервью с меткой PROMO_LABEL")
        void creditsMaxGiftBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_MAX);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(quotaService).creditInterviews(USER_ID, Payment.Product.PLAN_MAX.getGiftInterviews(),
                    GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата за секунду до дедлайна — начисление есть")
        void creditsGiftOneSecondBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_PRO);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(1);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(quotaService).creditInterviews(USER_ID, Payment.Product.PLAN_PRO.getGiftInterviews(),
                    GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата ровно в момент дедлайна — начисления нет")
        void doesNotCreditExactlyAtDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_PRO);
            Instant paidAt = GiftService.PROMO_UNTIL;

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verifyNoInteractions(quotaService);
        }

        @Test
        @DisplayName("Оплата после дедлайна — начисления нет")
        void doesNotCreditAfterDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_PRO);
            Instant paidAt = GiftService.PROMO_UNTIL.plusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verifyNoInteractions(quotaService);
        }
    }
}
