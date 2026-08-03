package ru.workbit.util;

import java.text.Normalizer;

/**
 * Приводит названия навыков и профессий к сравнимому виду. LLM возвращает канонические названия
 * с типографикой (неразрывный дефис, узкий неразрывный пробел), и без этой чистки они попадают
 * в словарь двойниками существующих записей, а выборка банка по имени промахивается.
 */
public final class DictText {
    private static final String DASHES = "[\\u2010-\\u2015\\u2212]";
    private static final String SPACES = "[\\u00A0\\u2007\\u2009\\u202F]";

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
}
