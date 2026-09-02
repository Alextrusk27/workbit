package ru.workbit.llm.config;

import com.anthropic.models.messages.OutputConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.anthropic")
public record AnthropicProperties(
        String baseUrl,
        String authToken,
        String model,
        OutputConfig.Effort effort
) {
}
