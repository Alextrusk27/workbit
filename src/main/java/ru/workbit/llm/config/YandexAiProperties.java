package ru.workbit.llm.config;

import com.openai.core.LogLevel;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.yandex-ai")
public record YandexAiProperties(
        String folder,
        String apiKey,
        Map<String, String> agents,
        LogLevel logLevel
) {
}
