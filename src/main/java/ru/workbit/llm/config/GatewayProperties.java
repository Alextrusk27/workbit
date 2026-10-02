package ru.workbit.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Хост и ключ шлюза, через который идут и Claude-агенты, и OpenAI-модели.
 */
@ConfigurationProperties(prefix = "llm.gateway")
public record GatewayProperties(
        String baseUrl,
        String authToken
) {
}
