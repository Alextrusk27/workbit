package ru.workbit.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Клиент OpenAI-моделей на том же шлюзе, что и Claude: ключ и хост общие, отличается маршрут.
 * Версия API у этого SDK входит в базовый URL, без неё запрос уходит в 404.
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, OpenAiProperties.class})
public class OpenAiConfig {
    private static final String API_VERSION = "/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public OpenAIClient gatewayOpenAiClient(GatewayProperties gateway) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(gateway.baseUrl() + API_VERSION)
                .apiKey(gateway.authToken())
                .timeout(TIMEOUT)
                .build();
    }
}
