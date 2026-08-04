package ru.workbit.auth.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.auth.model.LoginCode;
import ru.workbit.auth.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoginCodeJPARepository extends JpaRepository<@NotNull LoginCode, @NotNull UUID> {

    List<LoginCode> findAllByUserAndUsedAtIsNull(User user);

    Optional<LoginCode> findFirstByUserAndUsedAtIsNullOrderByCreatedDesc(User user);
}
