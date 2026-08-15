package ru.workbit.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RobokassaClientConfig {

    @Bean
    public RestClient robokassaRestClient(RestClient.Builder builder, RobokassaProperties props) {
        return builder.baseUrl(props.stateUrl()).build();
    }
}
