package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import ru.workbit.billing.config.RobokassaProperties;
import ru.workbit.billing.model.Payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobokassaService implements PaymentProvider {

    private final RobokassaProperties properties;

    @Override
    public String paymentUrl(Payment payment, String email) {
        String outSum = payment.getAmount().toPlainString();
        String invId = String.valueOf(payment.getInvId());
        String signature = sha256Hex(String.join(":",
                properties.merchantLogin(), outSum, invId, properties.password1()));

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.paymentUrl())
                .queryParam("MerchantLogin", properties.merchantLogin())
                .queryParam("OutSum", outSum)
                .queryParam("InvId", invId)
                .queryParam("Description", payment.getProduct().getDescription())
                .queryParam("SignatureValue", signature)
                .queryParam("Culture", "ru")
                .queryParam("Email", email);
        if (properties.test()) {
            builder.queryParam("IsTest", 1);
        }
        return builder.encode().toUriString();
    }

    @Override
    public Notification parseNotification(Map<String, String> params) {
        String outSum = params.get("OutSum");
        String invId = params.get("InvId");
        String signature = params.get("SignatureValue");
        if (outSum == null || invId == null || signature == null) {
            throw new IllegalArgumentException("Missing notification parameters");
        }

        String expected = sha256Hex(String.join(":", outSum, invId, properties.password2()));
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            log.warn("Robokassa notification with invalid signature, invId {}", invId);
            throw new IllegalArgumentException("Invalid signature");
        }
        return new Notification(Integer.parseInt(invId), new BigDecimal(outSum));
    }

    @Override
    public String notificationResponse(int invId) {
        return "OK" + invId;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
