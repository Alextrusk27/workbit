package ru.workbit.training.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.training.model.TrainingSession;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<@NotNull TrainingSession, @NotNull UUID> {

    boolean existsByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    Page<@NotNull TrainingSession> findAllByUserId(@NotNull UUID userId, Pageable pageable);

    Optional<TrainingSession> findByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    @Query("""
            SELECT ts FROM TrainingSession ts
            WHERE ts.userId = :userId AND LOWER(ts.skill) IN :skills
            ORDER BY ts.created DESC
            """)
    List<TrainingSession> findAllByUserIdAndLoweredSkillIn(@NotNull UUID userId, @NotNull Collection<String> skills);

    @Query("""
            SELECT ts FROM TrainingSession ts
            LEFT JOIN FETCH ts.questions q
            LEFT JOIN FETCH q.feedback
            WHERE ts.id = :id
            """)
    Optional<TrainingSession> findWithQuestionsById(@NotNull UUID id);
}
