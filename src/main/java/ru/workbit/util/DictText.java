package ru.workbit.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Приводит названия навыков и профессий к сравнимому виду. LLM возвращает канонические названия
 * с типографикой (неразрывный дефис, узкий неразрывный пробел), и без этой чистки они попадают
 * в словарь двойниками существующих записей, а выборка банка по имени промахивается.
 * Ключ сравнения ({@link #matchKey}) идёт дальше: одинаковые по смыслу названия с разным
 * порядком слов и предлогами («Разработчик на Java» и «Java-разработчик») дают один ключ,
 * по нему словарь и держит уникальность.
 */
public final class DictText {
    private static final String DASHES = "[\\u2010-\\u2015\\u2212]";
    private static final String SPACES = "[\\u00A0\\u2007\\u2009\\u202F]";
    private static final String NOT_WORD = "[^\\p{L}\\p{N}+#]+";
    private static final Set<String> STOP_WORDS = Set.of(
            "на", "по", "для", "и", "в", "с", "из", "о", "об",
            "of", "for", "and", "the", "in", "on", "with");

    private DictText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll(DASHES, "-")
                .replaceAll(SPACES, " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Ключ сравнения названия: значащие слова в нижнем регистре, без пунктуации, без повторов и
     * в алфавитном порядке. Плюс и решётка в слове сохраняются — иначе «C», «C++» и «C#»
     * схлопнулись бы в один навык.
     */
    public static String matchKey(String value) {
        return String.join(" ", matchTokens(value));
    }

    public static List<String> matchTokens(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }

        List<String> words = Arrays.stream(normalized.toLowerCase(Locale.ROOT)
                        .replaceAll(NOT_WORD, " ")
                        .strip()
                        .split(" "))
                .filter(word -> !word.isEmpty())
                .toList();

        List<String> significant = words.stream()
                .filter(word -> !STOP_WORDS.contains(word))
                .distinct()
                .sorted()
                .toList();
        return significant.isEmpty()
                ? words.stream().distinct().sorted().toList()
                : significant;
    }
}
