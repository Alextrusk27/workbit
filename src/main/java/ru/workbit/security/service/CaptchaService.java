package ru.workbit.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.security.config.CaptchaProperties;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaptchaService {
    private static final String STATUS_OK = "ok";

    private final CaptchaProperties properties;
    private final RestClient captchaRestClient;

    public void validate(String token, String ip) {
        if (!properties.enabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new ForbiddenException("Captcha validation failed");
        }

        ValidationResponse response;
        try {
            response = requestValidation(token, ip);
        } catch (RestClientException e) {
            log.warn("Captcha service unavailable, allowing request: {}", e.getMessage());
            return;
        }

        if (response == null || !STATUS_OK.equals(response.status())) {
            throw new ForbiddenException("Captcha validation failed");
        }
    }

    private ValidationResponse requestValidation(String token, String ip) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", properties.serverKey());
        form.add("token", token);
        form.add("ip", ip);

        return captchaRestClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ValidationResponse.class);
    }

    record ValidationResponse(String status, String message) {
    }
}
