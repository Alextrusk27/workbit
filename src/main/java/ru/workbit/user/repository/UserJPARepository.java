package ru.workbit.user.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.user.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserJPARepository extends JpaRepository<@NotNull User, @NotNull UUID> {

    Optional<User> findByEmail(String email);
}
