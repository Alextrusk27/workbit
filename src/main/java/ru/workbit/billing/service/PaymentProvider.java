package ru.workbit.billing.service;

import ru.workbit.billing.model.Payment;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentProvider {

    String paymentUrl(Payment payment, String email);

    Notification parseNotification(Map<String, String> params);

    String notificationResponse(int invId);

    record Notification(int invId, BigDecimal amount) {
    }
}
