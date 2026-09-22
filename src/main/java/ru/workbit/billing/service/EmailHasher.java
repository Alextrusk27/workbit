package ru.workbit.billing.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.workbit.util.EmailNormalizer;

/**
 * HMAC-SHA256 от адреса электронной почты. Признак «приветственные лимиты уже выдавались»
 * переживает удаление аккаунта, поэтому сам адрес в БД хранить нельзя, а простой SHA-256
 * перебирается по словарю адресов.
 */
@Component
@Slf4j
public class EmailHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String FINGERPRINT_INPUT = "workbit";
    private final byte[] key;

    public EmailHasher(@Value("${app.email.hash-secret}") String secret) {
        if (secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException("app.email.hash-secret is not set");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        log.info("Email hash secret fingerprint: {}", hash(FINGERPRINT_INPUT).substring(0, 8));
    }

    public String hash(String email) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] hashBytes = mac.doFinal(EmailNormalizer.normalize(email).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
