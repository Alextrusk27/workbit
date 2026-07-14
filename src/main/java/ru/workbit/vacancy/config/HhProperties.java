package ru.workbit.vacancy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hh.api")
public record HhProperties(
        String baseUrl,
        String userAgent,
        String appToken
) {
}
