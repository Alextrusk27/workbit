package ru.workbit.llm.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicConfig {
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    @Bean
    public AnthropicClient anthropicClient(AnthropicProperties props) {
        return AnthropicOkHttpClient.builder()
                .baseUrl(props.baseUrl())
                .authToken(props.authToken())
                .timeout(TIMEOUT)
                .build();
    }
}
