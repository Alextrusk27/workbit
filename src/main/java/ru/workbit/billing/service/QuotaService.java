package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.billing.repository.UsageEventRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final BillingAccountRepository billingAccountRepository;
    private final UsageEventRepository usageEventRepository;

    public QuotaResponse getQuota(UUID userId) {
        return effectiveQuota(userId);
    }

    public UsageResponse getUsage(UUID userId) {
        BillingAccount account = billingAccountRepository.findById(userId).orElse(null);
        List<UsageResponse.UsageEventResponse> events = usageEventRepository
                .findAllByUserIdOrderByAtDesc(userId).stream()
                .map(e -> new UsageResponse.UsageEventResponse(
                        e.getAt(), e.getKind(), e.getTarget(), e.getDelta(), e.getLabel()))
                .toList();

        if (account == null) {
            BillingAccount.Plan free = BillingAccount.Plan.FREE;
            return new UsageResponse(
                    new UsageResponse.UsageCounter(free.getInterviews(), free.getInterviews()),
                    new UsageResponse.UsageCounter(free.getTrainings(), free.getTrainings()),
                    events);
        }

        boolean planActive = isPlanActive(account);
        return new UsageResponse(
                planCounter(planActive, account.getPlanInterviewsLeft(),
                        account.getPlan().getInterviews()),
                planCounter(planActive, account.getPlanTrainingsLeft(),
                        account.getPlan().getTrainings()),
                events);
    }

    public void checkInterviewAvailable(UUID userId) {
        if (effectiveQuota(userId).planInterviewsLeft() == 0) {
            log.warn("Interview quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Interview quota exhausted");
        }
    }

    public void checkTrainingAvailable(UUID userId) {
        if (effectiveQuota(userId).planTrainingsLeft() == 0) {
            log.warn("Training quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Training quota exhausted");
        }
    }

    public void checkPaidPlan(UUID userId) {
        if (effectiveQuota(userId).plan() == BillingAccount.Plan.FREE) {
            log.warn("Paid plan required for user {}", userId);
            throw new ForbiddenException("Paid plan required");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debitInterview(UUID userId, String label) {
        insertIfAbsent(userId);
        if (billingAccountRepository.debitPlanInterview(userId, Instant.now()) == 0) {
            log.warn("Interview quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Interview quota exhausted");
        }
        saveSpendEvent(userId, UsageEvent.Target.INTERVIEW, label);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debitTraining(UUID userId, String label) {
        insertIfAbsent(userId);
        if (billingAccountRepository.debitPlanTraining(userId, Instant.now()) == 0) {
            log.warn("Training quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Training quota exhausted");
        }
        saveSpendEvent(userId, UsageEvent.Target.TRAINING, label);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void creditPlan(UUID userId, BillingAccount.Plan plan, String label) {
        insertIfAbsent(userId);
        Instant now = Instant.now();
        billingAccountRepository.creditPlan(userId, plan.name(),
                plan.getInterviews(), plan.getTrainings(), now);
        saveCreditEvent(userId, UsageEvent.Target.INTERVIEW, plan.getInterviews(), label, now);
        saveCreditEvent(userId, UsageEvent.Target.TRAINING, plan.getTrainings(), label, now);
        log.info("Credited plan {} to user {}", plan, userId);
    }

    private static QuotaResponse toEffective(BillingAccount account) {
        if (isPlanActive(account)) {
            return new QuotaResponse(account.getPlan(), account.getPlanExpiresAt(),
                    account.getPlanInterviewsLeft(), account.getPlanTrainingsLeft());
        }

        return new QuotaResponse(BillingAccount.Plan.FREE, null, 0, 0);
    }

    private static boolean isPlanActive(BillingAccount account) {
        return account.getPlan() == BillingAccount.Plan.FREE
                || account.getPlanExpiresAt().isAfter(Instant.now());
    }

    private static UsageResponse.UsageCounter planCounter(boolean planActive, int left, int total) {
        return planActive
                ? new UsageResponse.UsageCounter(left, total)
                : new UsageResponse.UsageCounter(0, 0);
    }

    private static QuotaResponse freeDefault() {
        BillingAccount.Plan free = BillingAccount.Plan.FREE;
        return new QuotaResponse(free, null, free.getInterviews(), free.getTrainings());
    }

    private QuotaResponse effectiveQuota(UUID userId) {
        return billingAccountRepository.findById(userId)
                .map(QuotaService::toEffective)
                .orElseGet(QuotaService::freeDefault);
    }

    private void insertIfAbsent(UUID userId) {
        BillingAccount.Plan free = BillingAccount.Plan.FREE;
        billingAccountRepository.insertIfAbsent(userId, free.getInterviews(), free.getTrainings());
    }

    private void saveSpendEvent(UUID userId, UsageEvent.Target target, String label) {
        usageEventRepository.save(UsageEvent.builder()
                .userId(userId)
                .kind(UsageEvent.Kind.SPEND)
                .target(target)
                .delta(1)
                .label(label)
                .build());
    }

    private void saveCreditEvent(UUID userId, UsageEvent.Target target, int delta,
                                 String label, Instant at) {
        usageEventRepository.save(UsageEvent.builder()
                .userId(userId)
                .at(at)
                .kind(UsageEvent.Kind.CREDIT)
                .target(target)
                .delta(delta)
                .label(label)
                .build());
    }
}
