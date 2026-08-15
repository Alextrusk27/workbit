package ru.workbit.billing.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.billing.model.UsageEvent;

import java.util.List;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<@NotNull UsageEvent, @NotNull UUID> {

    List<UsageEvent> findAllByUserIdOrderByAtDesc(UUID userId);
}
