package ru.workbit.auth.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.auth.model.VerificationToken;
import ru.workbit.auth.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenJPARepository extends JpaRepository<@NotNull VerificationToken, @NotNull UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    List<VerificationToken> findAllByUserAndTypeAndUsedAtIsNull(User user, VerificationToken.Type type);
}
