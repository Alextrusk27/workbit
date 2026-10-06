package ru.workbit.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Хост и ключ LLM-шлюза. */
@ConfigurationProperties(prefix = "llm.gateway")
public record GatewayProperties(
        String baseUrl,
        String authToken
) {
}
