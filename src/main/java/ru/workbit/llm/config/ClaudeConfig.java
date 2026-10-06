package ru.workbit.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewayProperties.class, ClaudeProperties.class})
public class ClaudeConfig {
    private static final String API_VERSION = "/v1";
    private static final Timeout TIMEOUT = Timeout.builder()
            .request(Duration.ofMinutes(10))
            .read(Duration.ofMinutes(3))
            .build();
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
