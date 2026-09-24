package ru.workbit.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.workbit.auth.model.LoginCode;
import ru.workbit.auth.model.User;

public interface LoginCodeJPARepository extends JpaRepository<@NotNull LoginCode, @NotNull UUID> {

    List<LoginCode> findAllByUserAndUsedAtIsNull(User user);

    Optional<LoginCode> findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(User user);

    @Modifying
    @Query("DELETE FROM LoginCode lc WHERE lc.expiresAt < :threshold")
    int deleteByExpiresAtBefore(@Param("threshold") Instant threshold);
}
