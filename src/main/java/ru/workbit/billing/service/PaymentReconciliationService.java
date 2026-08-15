package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.workbit.billing.model.Payment;
import ru.workbit.billing.repository.PaymentRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationService {

    private static final Duration MIN_AGE = Duration.ofSeconds(30);
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60_000)
    public void reconcilePending() {
        Instant now = Instant.now();
        List<Payment> pending = paymentRepository.findByStatusAndCreatedBetween(
                Payment.Status.PENDING, now.minus(MAX_AGE), now.minus(MIN_AGE));

        for (Payment payment : pending) {
            if (paymentProvider.isPaid(payment) && paymentService.confirmPaid(payment)) {
                log.warn("Payment {} (invId {}) credited by reconciliation, notification never arrived",
                        payment.getId(), payment.getInvId());
            }
        }
    }
}
