package ru.workbit.email.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String fromName,
        String fromMail,
        String baseUrl
) {
}
