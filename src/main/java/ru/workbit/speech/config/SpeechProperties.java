package ru.workbit.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Доступ к SpeechKit — секция {@code speech.yandex}.
 *
 * @param apiKey ключ сервисного аккаунта Yandex Cloud; тот же, что у llm-модуля
 * @param host   адрес сервиса распознавания
 * @param port   порт gRPC
 */
@ConfigurationProperties(prefix = "speech.yandex")
public record SpeechProperties(
        String apiKey,
        String host,
        int port
) {
}
