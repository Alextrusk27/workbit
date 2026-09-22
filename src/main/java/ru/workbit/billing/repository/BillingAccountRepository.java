package ru.workbit.billing.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.billing.model.BillingAccount;

public interface BillingAccountRepository extends JpaRepository<@NotNull BillingAccount, @NotNull UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BillingAccount a WHERE a.userId = :userId")
    Optional<BillingAccount> findForUpdate(UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO billing.account (user_id, limits)
            VALUES (:userId, 0)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID userId);

    @Modifying
    @Query(value = """
            UPDATE billing.account SET limits = limits - :cost
            WHERE user_id = :userId AND limits >= :cost AND limits_expire_at > :now
            """, nativeQuery = true)
    int debit(UUID userId, int cost, Instant now);

    @Modifying
    @Query(value = """
            UPDATE billing.account SET
                limits = (CASE WHEN limits_expire_at > :now THEN limits ELSE 0 END) + :limits,
                limits_expire_at = CAST(:now AS timestamptz) + INTERVAL '3 months',
                paid_at = COALESCE(paid_at, :now)
            WHERE user_id = :userId
            """, nativeQuery = true)
    void creditTopUp(UUID userId, int limits, Instant now);

    @Modifying
    @Query(value = """
            UPDATE billing.account SET
                limits = (CASE WHEN limits_expire_at > :now THEN limits ELSE 0 END) + :limits,
                limits_expire_at = CAST(:now AS timestamptz) + INTERVAL '3 months'
            WHERE user_id = :userId
            """, nativeQuery = true)
    void creditWelcome(UUID userId, int limits, Instant now);
}
