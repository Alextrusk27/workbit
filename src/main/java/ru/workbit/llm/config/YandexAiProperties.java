package ru.workbit.llm.config;

import com.openai.core.LogLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "llm.yandex-ai")
public record YandexAiProperties(
        String folder,
        String apiKey,
        Map<String, String> agents,
        LogLevel logLevel
) {
}
