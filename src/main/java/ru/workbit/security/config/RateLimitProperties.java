package ru.workbit.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
