package ru.workbit.llm.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewayProperties.class, AnthropicProperties.class})
public class AnthropicConfig {
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_RETRIES = 4;

    @Bean
    public AnthropicClient anthropicClient(GatewayProperties gateway) {
        return AnthropicOkHttpClient.builder()
                .baseUrl(gateway.baseUrl())
                .authToken(gateway.authToken())
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }
}
