package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.model.Payment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiftService {

    public static final String PROMO_LABEL = "Подарок за покупку";
    public static final Instant PROMO_UNTIL = LocalDate.of(2026, 10, 1)
            .atStartOfDay(ZoneId.of("Europe/Moscow"))
            .toInstant();

    private final QuotaService quotaService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void grantPromoGift(Payment payment, Instant paidAt) {
        if (!paidAt.isBefore(PROMO_UNTIL)) {
            return;
        }
        int interviews = payment.getProduct().getGiftInterviews();
        quotaService.creditInterviews(payment.getUserId(), interviews, PROMO_LABEL);
        log.info("Promo gift ({} interviews) granted to user {}",
                interviews, payment.getUserId());
    }
}
