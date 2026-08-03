package ru.workbit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DictText")
class DictTextTest {

    static Stream<Arguments> typography() {
        return Stream.of(
                Arguments.of("неразрывный дефис", "Java‑разработчик", "Java-разработчик"),
                Arguments.of("дефис-hyphen", "Java‐разработчик", "Java-разработчик"),
                Arguments.of("короткое тире", "Java–разработчик", "Java-разработчик"),
                Arguments.of("длинное тире", "Java—разработчик", "Java-разработчик"),
                Arguments.of("минус", "Java−разработчик", "Java-разработчик"),
                Arguments.of("узкий неразрывный пробел", "Spring Boot", "Spring Boot"),
                Arguments.of("неразрывный пробел", "Spring Boot", "Spring Boot"),
                Arguments.of("тонкий пробел", "Spring Boot", "Spring Boot"),
                Arguments.of("края и повторы пробелов", "  Spring   Boot  ", "Spring Boot"),
                Arguments.of("чистое значение", "Spring Boot", "Spring Boot"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("typography")
    @DisplayName("Приводит типографику к сравнимому виду")
    void normalizesTypography(String caseName, String input, String expected) {
        assertThat(DictText.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("null возвращается как есть")
    void keepsNull(String input) {
        assertThat(DictText.normalize(input)).isNull();
    }

    @Test
    @DisplayName("Регистр не трогает: сравнение имён идёт через lower() в запросах")
    void keepsCase() {
        assertThat(DictText.normalize("Spring BOOT")).isEqualTo("Spring BOOT");
    }
}
