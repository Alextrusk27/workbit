package ru.workbit.billing.service;

import java.math.BigDecimal;
import java.util.Map;
import ru.workbit.billing.model.Payment;

public interface PaymentProvider {

    String paymentUrl(Payment payment, String email);

    Notification parseNotification(Map<String, String> params);

    String notificationResponse(int invId);

    boolean isPaid(Payment payment);

    record Notification(int invId, BigDecimal amount) {
    }
}
