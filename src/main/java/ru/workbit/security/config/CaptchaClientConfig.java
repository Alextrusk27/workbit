package ru.workbit.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CaptchaClientConfig {

    @Bean
    public RestClient captchaRestClient(RestClient.Builder builder, CaptchaProperties props) {
        return builder.baseUrl(props.validateUrl()).build();
    }
}
