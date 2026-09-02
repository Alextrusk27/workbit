package ru.workbit.llm.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(AnthropicProperties props) {
        return AnthropicOkHttpClient.builder()
                .baseUrl(props.baseUrl())
                .authToken(props.authToken())
                .build();
    }
}
