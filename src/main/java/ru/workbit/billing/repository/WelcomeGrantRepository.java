package ru.workbit.billing.repository;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.billing.model.WelcomeGrant;

public interface WelcomeGrantRepository extends JpaRepository<@NotNull WelcomeGrant, @NotNull String> {

    @Modifying
    @Query(value = """
            INSERT INTO billing.welcome_grant (email_hash, granted_at)
            VALUES (:emailHash, :now)
            ON CONFLICT (email_hash) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(String emailHash, Instant now);
}
