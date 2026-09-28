package ru.workbit.billing.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.billing.model.UsageEvent;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    List<UsageEvent> findAllByUserIdOrderByAtDesc(UUID userId);

    boolean existsByUserIdAndOperation(UUID userId, UsageEvent.Operation operation);
}
