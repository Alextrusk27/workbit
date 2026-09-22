package ru.workbit.util;

import java.util.Locale;

/**
 * Приводит адрес электронной почты к каноническому виду. Уникальный индекс
 * {@code auth.users.email} в Postgres регистрозависимый, поэтому без нормализации
 * {@code User@mail.ru} и {@code user@mail.ru} — два разных пользователя.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
