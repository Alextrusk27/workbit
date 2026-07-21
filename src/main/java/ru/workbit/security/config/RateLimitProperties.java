package ru.workbit.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        int limit,
        Duration window,
        Bucket suggest,
        Bucket normalize
) {
    public record Bucket(int limit, Duration window) {
    }
}
