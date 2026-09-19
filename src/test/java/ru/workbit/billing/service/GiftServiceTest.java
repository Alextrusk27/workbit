package ru.workbit.billing.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.model.Payment;

@ExtendWith(MockitoExtension.class)
@DisplayName("GiftServiceTest")
class GiftServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    LimitService limitService;

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
        @DisplayName("Оплата пакета до дедлайна — начисляет 10 лимитов с меткой PROMO_LABEL")
        void creditsGiftForPackBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PACK_50);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(limitService).creditGift(USER_ID, GiftService.GIFT_LIMITS, GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата legacy-продукта до дедлайна — начисляет те же 10 лимитов")
        void creditsGiftForLegacyProductBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PLAN_PRO);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(limitService).creditGift(USER_ID, GiftService.GIFT_LIMITS, GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата за секунду до дедлайна — начисление есть")
        void creditsGiftOneSecondBeforeDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PACK_50);
            Instant paidAt = GiftService.PROMO_UNTIL.minusSeconds(1);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verify(limitService).creditGift(USER_ID, GiftService.GIFT_LIMITS, GiftService.PROMO_LABEL);
        }

        @Test
        @DisplayName("Оплата ровно в момент дедлайна — начисления нет")
        void doesNotCreditExactlyAtDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PACK_50);
            Instant paidAt = GiftService.PROMO_UNTIL;

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verifyNoInteractions(limitService);
        }

        @Test
        @DisplayName("Оплата после дедлайна — начисления нет")
        void doesNotCreditAfterDeadline() {
            // given
            Payment payment = aPayment(Payment.Product.PACK_50);
            Instant paidAt = GiftService.PROMO_UNTIL.plusSeconds(3600);

            // when
            giftService.grantPromoGift(payment, paidAt);

            // then
            verifyNoInteractions(limitService);
        }
    }
}
