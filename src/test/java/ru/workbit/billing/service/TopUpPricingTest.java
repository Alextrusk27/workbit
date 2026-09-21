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

        @ParameterizedTest(name = "{0} лимитов проходит")
        @ValueSource(ints = {10, 20, 490, 500})
        void acceptsValidLimits(int limits) {
            assertThatCode(() -> TopUpPricing.validate(limits)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} лимитов — вне диапазона")
        @ValueSource(ints = {0, 510, -10})
        void rejectsOutOfRange(int limits) {
            assertThatThrownBy(() -> TopUpPricing.validate(limits))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between");
        }

        @ParameterizedTest(name = "{0} лимитов — не кратно 10")
        @ValueSource(ints = {15, 101, 499})
        void rejectsNotMultipleOfStep(int limits) {
            assertThatThrownBy(() -> TopUpPricing.validate(limits))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("multiple");
        }
    }

    @Nested
    @DisplayName("Amount")
    class Amount {

        @ParameterizedTest(name = "{0} лимитов: {1} ₽/лимит, итого {2} ₽")
        @CsvSource({
                "10, 15.00, 150.00",
                "40, 15.00, 600.00",
                "90, 15.00, 1350.00",
                "100, 13.50, 1350.00",
                "190, 13.50, 2565.00",
                "200, 12.00, 2400.00",
                "300, 11.00, 3300.00",
                "490, 11.00, 5390.00",
                "500, 10.00, 5000.00",
        })
        void appliesTierPriceToWholeVolume(int limits, String perLimit, String amount) {
            assertThat(TopUpPricing.pricePerLimit(limits)).isEqualByComparingTo(perLimit);
            assertThat(TopUpPricing.amount(limits)).isEqualByComparingTo(amount);
            assertThat(TopUpPricing.amount(limits).scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("Ниже минимума — IllegalArgumentException")
        void throwsBelowMinimum() {
            assertThatThrownBy(() -> TopUpPricing.pricePerLimit(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Метка пополнения содержит число лимитов")
    void labelContainsLimits() {
        assertThat(TopUpPricing.label(200)).isEqualTo("Пополнение на 200 лимитов");
    }
}
