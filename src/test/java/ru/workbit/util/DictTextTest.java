package ru.workbit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
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

    @Nested
    @DisplayName("MatchKey")
    class MatchKey {

        @Test
        @DisplayName("Разный порядок слов и предлог не меняют ключ (\"Разработчик на Java\" и \"Java-разработчик\")")
        void sameKeyRegardlessOfWordOrderAndPreposition() {
            assertThat(DictText.matchKey("Разработчик на Java")).isEqualTo(DictText.matchKey("Java-разработчик"));
        }

        @Test
        @DisplayName("Синоним с общим словом - другой навык, другой ключ (\"Spring Boot Jpa\" и \"Spring JPA\")")
        void differentKeysForDifferentSkillsSharingAWord() {
            assertThat(DictText.matchKey("Spring Boot Jpa")).isNotEqualTo(DictText.matchKey("Spring JPA"));
        }

        @Test
        @DisplayName("C, C++, C# - три разных ключа: + и # сохраняются внутри слова")
        void keepsPlusAndHashProducingDistinctKeys() {
            String c = DictText.matchKey("C");
            String cpp = DictText.matchKey("C++");
            String cSharp = DictText.matchKey("C#");

            assertThat(c).isNotEqualTo(cpp);
            assertThat(c).isNotEqualTo(cSharp);
            assertThat(cpp).isNotEqualTo(cSharp);
        }

        @Test
        @DisplayName("Повторы слов схлопываются")
        void collapsesDuplicateWords() {
            assertThat(DictText.matchKey("Java Java Developer")).isEqualTo(DictText.matchKey("Java Developer"));
        }

        @Test
        @DisplayName("Порядок слов не влияет на ключ")
        void wordOrderDoesNotAffectKey() {
            assertThat(DictText.matchKey("Spring Boot")).isEqualTo(DictText.matchKey("Boot Spring"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "!!!", " - "})
        @DisplayName("Пустой и чисто пунктуационный ввод - пустой ключ")
        void emptyKeyForBlankOrPunctuationOnlyInput(String input) {
            assertThat(DictText.matchKey(input)).isEmpty();
        }

        @Test
        @DisplayName("Типографский дефис и неразрывный пробел не меняют ключ")
        void typographicCharactersDoNotChangeKey() {
            assertThat(DictText.matchKey("Java‑разработчик")).isEqualTo(DictText.matchKey("Java-разработчик"));
            assertThat(DictText.matchKey("Spring Boot")).isEqualTo(DictText.matchKey("Spring Boot"));
        }
    }

    @Nested
    @DisplayName("MatchTokens")
    class MatchTokens {

        @Test
        @DisplayName("null или пустой ввод - пустой список токенов")
        void emptyListForNullOrBlankInput() {
            assertThat(DictText.matchTokens(null)).isEmpty();
            assertThat(DictText.matchTokens("")).isEmpty();
        }

        @Test
        @DisplayName("Ввод из одних стоп-слов не теряет всё - возвращаются исходные слова")
        void stopWordsOnlyInputKeepsOriginalWords() {
            assertThat(DictText.matchTokens("на по для")).containsExactlyInAnyOrder("на", "по", "для");
        }

        @Test
        @DisplayName("Стоп-слова отсеиваются, когда среди слов есть значащие")
        void filtersOutStopWordsWhenSignificantWordsPresent() {
            assertThat(DictText.matchTokens("Разработчик на Java")).isEqualTo(List.of("java", "разработчик"));
        }
    }
}
