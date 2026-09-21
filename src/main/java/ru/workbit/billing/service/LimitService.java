package ru.workbit.billing.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.dto.BalanceResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.billing.repository.UsageEventRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

@Service
@RequiredArgsConstructor
@Slf4j
public class LimitService {

    public static final int WELCOME_LIMITS = 20;
    static final String WELCOME_LABEL = "Приветственные лимиты";
    static final String EXPIRE_LABEL = "Срок действия лимитов истёк";

    private final BillingAccountRepository billingAccountRepository;
    private final UsageEventRepository usageEventRepository;

    @Transactional
    public BalanceResponse getBalance(UUID userId) {
        insertIfAbsent(userId);
        return toBalance(load(userId));
    }

    @Transactional
    public UsageResponse getUsage(UUID userId) {
        insertIfAbsent(userId);
        BalanceResponse balance = toBalance(load(userId));
        List<UsageResponse.UsageEventResponse> events = usageEventRepository
                .findAllByUserIdOrderByAtDesc(userId).stream()
                .map(e -> new UsageResponse.UsageEventResponse(
                        e.getAt(), e.getKind(), e.getOperation(), e.getDelta(), e.getLabel()))
                .toList();
        return new UsageResponse(balance.limits(), balance.expiresAt(), balance.paid(), events);
    }

    @Transactional
    public void check(UUID userId, UsageEvent.Operation operation) {
        insertIfAbsent(userId);
        if (toBalance(load(userId)).limits() < operation.getCost()) {
            log.warn("Not enough limits for {} for user {}", operation, userId);
            throw new PaymentRequiredException("Not enough limits");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(UUID userId, UsageEvent.Operation operation, String label) {
        insertIfAbsent(userId);
        if (billingAccountRepository.debit(userId, operation.getCost(), Instant.now()) == 0) {
            log.warn("Not enough limits for {} for user {}", operation, userId);
            throw new PaymentRequiredException("Not enough limits");
        }
        saveEvent(userId, UsageEvent.Kind.SPEND, operation, operation.getCost(), label, Instant.now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void creditTopUp(UUID userId, int limits, String label) {
        insertIfAbsent(userId);
        Instant now = Instant.now();
        BillingAccount account = load(userId);
        if (account.getLimits() > 0 && !isActive(account, now)) {
            saveEvent(userId, UsageEvent.Kind.SPEND, UsageEvent.Operation.EXPIRE,
                    account.getLimits(), EXPIRE_LABEL, now);
        }
        billingAccountRepository.creditTopUp(userId, limits, now);
        saveEvent(userId, UsageEvent.Kind.CREDIT, UsageEvent.Operation.TOPUP, limits, label, now);
        log.info("Credited {} limits to user {}", limits, userId);
    }

    @Transactional
    public void requirePaid(UUID userId) {
        insertIfAbsent(userId);
        if (load(userId).getPaidAt() == null) {
            log.warn("Purchase required for user {}", userId);
            throw new ForbiddenException("Purchase required");
        }
    }

    private static BalanceResponse toBalance(BillingAccount account) {
        boolean paid = account.getPaidAt() != null;
        return isActive(account, Instant.now())
                ? new BalanceResponse(account.getLimits(), account.getLimitsExpireAt(), paid)
                : new BalanceResponse(0, null, paid);
    }

    private static boolean isActive(BillingAccount account, Instant now) {
        return account.getLimitsExpireAt() != null && account.getLimitsExpireAt().isAfter(now);
    }

    private void insertIfAbsent(UUID userId) {
        Instant now = Instant.now();
        if (billingAccountRepository.insertIfAbsent(userId, WELCOME_LIMITS, now) == 1) {
            saveEvent(userId, UsageEvent.Kind.CREDIT, UsageEvent.Operation.WELCOME,
                    WELCOME_LIMITS, WELCOME_LABEL, now);
            log.info("Granted welcome limits to user {}", userId);
        }
    }

    private BillingAccount load(UUID userId) {
        return billingAccountRepository.findById(userId).orElseThrow();
    }

    private void saveEvent(UUID userId, UsageEvent.Kind kind, UsageEvent.Operation operation, int delta,
                           String label, Instant at) {
        usageEventRepository.save(UsageEvent.builder()
                .userId(userId)
                .at(at)
                .kind(kind)
                .operation(operation)
                .delta(delta)
                .label(label)
                .build());
    }
}
