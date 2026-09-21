package ru.workbit.billing.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.model.Payment;
import ru.workbit.billing.repository.PaymentRepository;
import ru.workbit.exception.NotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final LimitService limitService;

    @Transactional
    public PaymentCreateResponse create(UUID userId, int limits, String email) {
        TopUpPricing.validate(limits);
        Payment payment = paymentRepository.save(Payment.builder()
                .invId(paymentRepository.nextInvId())
                .userId(userId)
                .limits(limits)
                .amount(TopUpPricing.amount(limits))
                .status(Payment.Status.PENDING)
                .build());
        log.info("Created payment {} (invId {}) for user {}: {} limits for {}",
                payment.getId(), payment.getInvId(), userId, limits, payment.getAmount());
        return new PaymentCreateResponse(payment.getId(), paymentProvider.paymentUrl(payment, email));
    }

    @Transactional
    public String confirm(Map<String, String> params) {
        PaymentProvider.Notification notification = paymentProvider.parseNotification(params);
        Payment payment = paymentRepository.findByInvId(notification.invId())
                .orElseThrow(() -> {
                    log.warn("Payment notification for unknown invId {}", notification.invId());
                    return new IllegalArgumentException("Unknown payment");
                });
        if (notification.amount().compareTo(payment.getAmount()) != 0) {
            log.warn("Payment notification with wrong amount for invId {}: {}",
                    payment.getInvId(), notification.amount());
            throw new IllegalArgumentException("Amount mismatch");
        }

        confirmPaid(payment);
        return paymentProvider.notificationResponse(payment.getInvId());
    }

    @Transactional
    public boolean confirmPaid(Payment payment) {
        Instant paidAt = Instant.now();
        if (paymentRepository.markPaid(payment.getId(), paidAt) != 1) {
            return false;
        }

        limitService.creditTopUp(payment.getUserId(), payment.getLimits(), TopUpPricing.label(payment.getLimits()));
        log.info("Payment {} (invId {}) confirmed for user {}",
                payment.getId(), payment.getInvId(), payment.getUserId());
        return true;
    }

    public PaymentStatusResponse get(UUID id, UUID userId) {
        Payment payment = paymentRepository.findById(id)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return new PaymentStatusResponse(payment.getStatus(), payment.getLimits(), payment.getAmount());
    }
}
