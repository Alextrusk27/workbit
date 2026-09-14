package ru.workbit.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.openai")
public record OpenAiProperties(
        String model
) {
}
