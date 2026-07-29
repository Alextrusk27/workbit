package ru.workbit.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "speech.yandex")
public record SpeechProperties(
        String apiKey,
        String host,
        int port
) {
}
