package ru.workbit.vacancy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HhProperties.class)
public class HhClientConfig {

    @Bean
    public RestClient hhRestClient(RestClient.Builder builder, HhProperties props) {
        return builder
                .baseUrl(props.baseUrl())
                .defaultHeader("User-Agent", props.userAgent())
                .defaultHeader("Authorization", "Bearer " + props.appToken())
                .build();
    }
}
