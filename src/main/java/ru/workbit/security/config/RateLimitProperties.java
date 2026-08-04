package ru.workbit.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        int limit,
        Duration window,
        Bucket verifyCode,
        Bucket suggest,
        Bucket normalize,
        Bucket stt
) {
    public record Bucket(int limit, Duration window) {
    }
}
