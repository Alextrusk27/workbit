package ru.workbit.speech.dto;

/**
 * Событие распознавания в том виде, в котором оно уходит браузеру: JSON {@code {type, text}}
 * текстовым сообщением WebSocket. Повторяет {@link SttResult} и добавляет вид {@link Type#ERROR}.
 *
 * @param type вид события
 * @param text распознанный текст, а для ошибки — её описание
 */
public record SttEvent(Type type, String text) {

    /**
     * Вид события. Первые три повторяют {@link SttResult.Kind}, {@link #ERROR} — только для ошибок.
     */
    public enum Type {
        /** Промежуточная гипотеза: показывать бегущей подсказкой, в поле ответа не подставлять. */
        PARTIAL,
        /** Итоговый текст фразы без нормализации. */
        FINAL,
        /** Нормализованный текст фразы — именно он идёт в поле ответа. */
        REFINEMENT,
        /** Распознавание оборвалось; следом сервер закрывает соединение. */
        ERROR
    }

    /**
     * Переносит гипотезу распознавания в событие для браузера.
     *
     * @param result гипотеза от клиента SpeechKit
     * @return событие того же вида с тем же текстом
     */
    public static SttEvent of(SttResult result) {
        return new SttEvent(Type.valueOf(result.kind().name()), result.text());
    }

    /**
     * Собирает событие об ошибке распознавания.
     *
     * @param message описание для пользователя, без внутренних подробностей
     * @return событие вида {@link Type#ERROR}
     */
    public static SttEvent error(String message) {
        return new SttEvent(Type.ERROR, message);
    }
}
