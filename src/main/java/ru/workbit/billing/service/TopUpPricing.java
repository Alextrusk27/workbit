package ru.workbit.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class TopUpPricing {

    public static final int MIN_LIMITS = 10;
    public static final int MAX_LIMITS = 500;
    public static final int STEP = 10;

    public static final List<Tier> TIERS = List.of(
            new Tier(500, new BigDecimal("10.00")),
            new Tier(300, new BigDecimal("11.00")),
            new Tier(200, new BigDecimal("12.00")),
            new Tier(100, new BigDecimal("13.50")),
            new Tier(10, new BigDecimal("15.00")));

    private TopUpPricing() {
    }

    public static void validate(int limits) {
        if (limits < MIN_LIMITS || limits > MAX_LIMITS) {
            throw new IllegalArgumentException("Limits must be between " + MIN_LIMITS + " and " + MAX_LIMITS);
        }
        if (limits % STEP != 0) {
            throw new IllegalArgumentException("Limits must be a multiple of " + STEP);
        }
    }

    public static BigDecimal pricePerLimit(int limits) {
        return TIERS.stream()
                .filter(tier -> limits >= tier.fromLimits())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Limits below minimum"))
                .pricePerLimit();
    }

    public static BigDecimal amount(int limits) {
        return pricePerLimit(limits).multiply(BigDecimal.valueOf(limits))
                .setScale(0, RoundingMode.DOWN)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String label(int limits) {
        return "Пополнение на " + limits + " лимитов";
    }

    public record Tier(int fromLimits, BigDecimal pricePerLimit) {
    }
}
