package ru.workbit.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.robokassa")
public record RobokassaProperties(
        String merchantLogin,
        String password1,
        String password2,
        boolean test,
        String paymentUrl,
        String stateUrl
) {
}
