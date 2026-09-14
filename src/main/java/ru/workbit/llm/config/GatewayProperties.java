package ru.workbit.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Хост и ключ шлюза, через который идут и Claude-агенты, и OpenAI-модели: провайдер один,
 * маршруты у него разные.
 */
@ConfigurationProperties(prefix = "llm.gateway")
public record GatewayProperties(
        String baseUrl,
        String authToken
) {
}
