package ru.workbit.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.captcha")
public record CaptchaProperties(
        boolean enabled,
        String serverKey,
        String validateUrl
) {
}
