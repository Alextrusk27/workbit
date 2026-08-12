package ru.workbit.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.model.BillingAccount;
import ru.workbit.billing.repository.BillingAccountRepository;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.PaymentRequiredException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotaService {

    private final BillingAccountRepository billingAccountRepository;

    public QuotaResponse getQuota(UUID userId) {
        return effectiveQuota(userId);
    }

    public void checkInterviewAvailable(UUID userId) {
        QuotaResponse quota = effectiveQuota(userId);
        if (quota.planInterviewsLeft() + quota.packInterviewsLeft() == 0) {
            log.warn("Interview quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Interview quota exhausted");
        }
    }

    public void checkTrainingAvailable(UUID userId) {
        QuotaResponse quota = effectiveQuota(userId);
        if (quota.planTrainingsLeft() + quota.packTrainingsLeft() == 0) {
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
    public void debitInterview(UUID userId) {
        insertIfAbsent(userId);
        if (billingAccountRepository.debitPlanInterview(userId, Instant.now()) == 0
                && billingAccountRepository.debitPackInterview(userId) == 0) {
            log.warn("Interview quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Interview quota exhausted");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void debitTraining(UUID userId) {
        insertIfAbsent(userId);
        if (billingAccountRepository.debitPlanTraining(userId, Instant.now()) == 0
                && billingAccountRepository.debitPackTraining(userId) == 0) {
            log.warn("Training quota exhausted for user {}", userId);
            throw new PaymentRequiredException("Training quota exhausted");
        }
    }

    private static QuotaResponse toEffective(BillingAccount account) {
        boolean planActive = account.getPlan() == BillingAccount.Plan.FREE
                || account.getPlanExpiresAt().isAfter(Instant.now());
        if (planActive) {
            return new QuotaResponse(account.getPlan(), account.getPlanExpiresAt(),
                    account.getPlanInterviewsLeft(), account.getPlanTrainingsLeft(),
                    account.getPackInterviewsLeft(), account.getPackTrainingsLeft());
        }

        return new QuotaResponse(BillingAccount.Plan.FREE, null, 0, 0,
                account.getPackInterviewsLeft(), account.getPackTrainingsLeft());
    }

    private static QuotaResponse freeDefault() {
        BillingAccount.Plan free = BillingAccount.Plan.FREE;
        return new QuotaResponse(free, null, free.getInterviews(), free.getTrainings(), 0, 0);
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
}
