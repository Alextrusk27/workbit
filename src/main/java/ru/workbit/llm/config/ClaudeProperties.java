package ru.workbit.llm.config;

import com.openai.models.ReasoningEffort;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.claude")
public record ClaudeProperties(
        String model,
        ReasoningEffort effort
) {
}
