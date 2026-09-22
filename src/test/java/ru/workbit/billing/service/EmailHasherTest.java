package ru.workbit.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmailHasherTest")
class EmailHasherTest {

    // Контрольный вектор: ключ — байты самой строки секрета, без декодирования Base64.
    // Тот же результат даёт pgcrypto (hmac) в миграции V6 и openssl dgst -hmac.
    private static final String SECRET = "S3cr3tKeyBase64==";
    private static final String EMAIL = "user@x.ru";
    private static final String EXPECTED_HASH = "wMCP7diBt87Pro7+30YNjnXv+7tNJdasZORMvr6uTpc=";

    @Test
    @DisplayName("Контрольный вектор: HMAC-SHA256 в стандартном Base64")
    void matchesReferenceVector() {
        // when
        String hash = new EmailHasher(SECRET).hash(EMAIL);

        // then
        assertThat(hash).isEqualTo(EXPECTED_HASH);
    }

    @Test
    @DisplayName("Нормализует вход сам: регистр и пробелы по краям хеш не меняют")
    void normalizesInput() {
        // given
        EmailHasher hasher = new EmailHasher(SECRET);

        // when / then
        assertThat(hasher.hash("  User@X.ru  ")).isEqualTo(EXPECTED_HASH);
    }

    @Test
    @DisplayName("Неразрешённый плейсхолдер вместо секрета - IllegalStateException")
    void failsOnUnresolvedPlaceholder() {
        // when / then
        assertThatThrownBy(() -> new EmailHasher("${EMAIL_HASH_SECRET}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
