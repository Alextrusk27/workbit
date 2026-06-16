package ru.workbit.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String fromName,
        String fromMail,
        String baseUrl,
        int resetTtlMinutes
) {
}
