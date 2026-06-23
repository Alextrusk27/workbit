package ru.workbit.llm.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(YandexAiProperties.class)
public class YandexAiConfig {

    @Bean
    public OpenAIClient openAiClient(YandexAiProperties props) {
        return OpenAIOkHttpClient.builder()
                .apiKey(props.apiKey())
                .baseUrl("https://ai.api.cloud.yandex.net/v1")
                .organization(props.folder())
                .build();
    }
}
