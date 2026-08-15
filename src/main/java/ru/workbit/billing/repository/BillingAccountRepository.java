package ru.workbit.billing.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.billing.model.BillingAccount;

import java.time.Instant;
import java.util.UUID;

public interface BillingAccountRepository extends JpaRepository<@NotNull BillingAccount, @NotNull UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO billing.account (user_id, plan, plan_interviews_left, plan_trainings_left)
            VALUES (:userId, 'FREE', :freeInterviews, :freeTrainings)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(UUID userId, int freeInterviews, int freeTrainings);

    @Modifying
    @Query(value = """
            UPDATE billing.account SET plan_interviews_left = plan_interviews_left - 1
            WHERE user_id = :userId AND plan_interviews_left > 0
              AND (plan = 'FREE' OR plan_expires_at > :now)
            """, nativeQuery = true)
    int debitPlanInterview(UUID userId, Instant now);

    @Modifying
    @Query(value = """
            UPDATE billing.account SET plan_trainings_left = plan_trainings_left - 1
            WHERE user_id = :userId AND plan_trainings_left > 0
              AND (plan = 'FREE' OR plan_expires_at > :now)
            """, nativeQuery = true)
    int debitPlanTraining(UUID userId, Instant now);
}
