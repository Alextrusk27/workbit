package ru.workbit.auth.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.workbit.auth.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJPARepository extends JpaRepository<@NotNull User, @NotNull UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByLastSeenBeforeAndDeletionWarnedAtIsNull(Instant threshold);

    @Modifying
    @Query("DELETE FROM User u WHERE u.deletionWarnedAt < :threshold")
    int deleteByDeletionWarnedAtBefore(@Param("threshold") Instant threshold);
}
