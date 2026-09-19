package ru.workbit.billing.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.model.Payment;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiftService {

    public static final String PROMO_LABEL = "Подарок за покупку";
    public static final int GIFT_LIMITS = 10;
    public static final Instant PROMO_UNTIL = LocalDate.of(2026, 11, 1)
            .atStartOfDay(ZoneId.of("Europe/Moscow"))
            .toInstant();

    private final LimitService limitService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void grantPromoGift(Payment payment, Instant paidAt) {
        if (!paidAt.isBefore(PROMO_UNTIL)) {
            return;
        }
        limitService.creditGift(payment.getUserId(), GIFT_LIMITS, PROMO_LABEL);
        log.info("Promo gift ({} limits) granted to user {}", GIFT_LIMITS, payment.getUserId());
    }
}
