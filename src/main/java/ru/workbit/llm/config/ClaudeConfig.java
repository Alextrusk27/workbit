package ru.workbit.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Клиент Claude-агентов: OpenAI-совместимый маршрут шлюза, тот же, что у нормализатора, но со своими
 * таймаутом и повторами - отчёт по интервью генерируется больше минуты. Нативный Messages API не
 * используется: большие запросы шлюз отдаёт с ожиданием 20-40 с, а Opus 5.5 там не принимает effort.
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, ClaudeProperties.class})
public class ClaudeConfig {
    private static final String API_VERSION = "/v1";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_RETRIES = 4;

    @Bean
    public OpenAIClient gatewayClaudeClient(GatewayProperties gateway) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(gateway.baseUrl() + API_VERSION)
                .apiKey(gateway.authToken())
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }
}
