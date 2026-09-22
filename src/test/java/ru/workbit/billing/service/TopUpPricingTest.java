package ru.workbit.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TopUpPricingTest")
class TopUpPricingTest {

    @Nested
    @DisplayName("Validate")
    class Validate {

        @ParameterizedTest(name = "{0} лимитов: {1} ₽")
        @CsvSource({
                "10, 150.00",
                "40, 600.00",
                "50, 750.00",
                "90, 1350.00",
                "100, 1350.00",
                "180, 2190.00",
                "190, 2295.00",
                "200, 2400.00",
                "280, 3120.00",
                "300, 3300.00",
                "350, 3750.00",
                "400, 4200.00",
                "460, 4680.00",
                "490, 4920.00",
                "500, 5000.00",
        })
        void flatBelowDiscountThenInterpolatesBetweenTierAnchors(int limits, String amount) {
            assertThat(TopUpPricing.amount(limits)).isEqualByComparingTo(amount);
            assertThat(TopUpPricing.amount(limits).scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Больше лимитов никогда не стоит дешевле")
        void amountIsMonotonic() {
            for (int limits = TopUpPricing.MIN_LIMITS + TopUpPricing.STEP; limits <= TopUpPricing.MAX_LIMITS;
                    limits += TopUpPricing.STEP) {
                assertThat(TopUpPricing.amount(limits))
                        .as("%d vs %d", limits, limits - TopUpPricing.STEP)
                        .isGreaterThanOrEqualTo(TopUpPricing.amount(limits - TopUpPricing.STEP));
            }
        }

        @Test
        @DisplayName("Ниже минимума — IllegalArgumentException")
        void throwsBelowMinimum() {
            assertThatThrownBy(() -> TopUpPricing.amount(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Метка пополнения содержит число лимитов")
    void labelContainsLimits() {
        assertThat(TopUpPricing.label(200)).isEqualTo("Пополнение на 200 лимитов");
    }
}
