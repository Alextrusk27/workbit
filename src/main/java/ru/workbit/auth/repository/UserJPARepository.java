package ru.workbit.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.workbit.auth.model.User;

public interface UserJPARepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByLastSeenBeforeAndDeletionWarnedAtIsNull(Instant threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findByDeletionWarnedAtBefore(Instant threshold);

    @Modifying
    @Query("DELETE FROM User u WHERE u.deletionWarnedAt < :threshold")
    int deleteByDeletionWarnedAtBefore(@Param("threshold") Instant threshold);
}
