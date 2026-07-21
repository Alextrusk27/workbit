package ru.workbit.interview.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OfferProbabilityTest")
class OfferProbabilityTest {

    @Nested
    @DisplayName("FromString")
    class FromString {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @DisplayName("Распознаёт имя константы и русский лейбл, регистронезависимо, с обрезкой пробелов")
        @CsvSource({
                "LOW, LOW",
                "MEDIUM, MEDIUM",
                "HIGH, HIGH",
                "low, LOW",
                "High, HIGH",
                "'  medium  ', MEDIUM",
                "Низкая, LOW",
                "Средняя, MEDIUM",
                "Высокая, HIGH",
                "низкая, LOW",
                "СРЕДНЯЯ, MEDIUM",
                "высокая, HIGH"
        })
        void resolvesKnownValues(String input, InterviewReport.OfferProbability expected) {
            // when
            Optional<InterviewReport.OfferProbability> result = InterviewReport.OfferProbability.fromString(input);

            // then
            assertThat(result).contains(expected);
        }

        @Test
        @DisplayName("Возвращает пустой Optional, когда значение null")
        void returnsEmptyWhenNull() {
            // when
            Optional<InterviewReport.OfferProbability> result = InterviewReport.OfferProbability.fromString(null);

            // then
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("Возвращает пустой Optional для пустой или пробельной строки")
        @ValueSource(strings = {"", "   "})
        void returnsEmptyWhenBlank(String input) {
            // when
            Optional<InterviewReport.OfferProbability> result = InterviewReport.OfferProbability.fromString(input);

            // then
            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @DisplayName("Возвращает пустой Optional для нераспознанного значения")
        @ValueSource(strings = {"unknown", "Средне-высокая"})
        void returnsEmptyWhenUnknown(String input) {
            // when
            Optional<InterviewReport.OfferProbability> result = InterviewReport.OfferProbability.fromString(input);

            // then
            assertThat(result).isEmpty();
        }
    }
}
