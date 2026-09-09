package ru.workbit.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(YandexAiProperties.class)
public class YandexAiConfig {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    @Bean
    @Primary
    public OpenAIClient openAiClient(YandexAiProperties props) {
        return OpenAIOkHttpClient.builder()
                .apiKey(props.apiKey())
                .baseUrl("https://ai.api.cloud.yandex.net/v1")
                .organization(props.folder())
                .logLevel(props.logLevel())
                .timeout(REQUEST_TIMEOUT)
                .putHeader("x-data-logging-enabled", "false")
                .build();
    }
}
