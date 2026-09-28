package ru.workbit.training.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.training.model.TrainingSession;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "report")
    Page<TrainingSession> findAllByUserId(UUID userId, Pageable pageable);

    Optional<TrainingSession> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "report")
    @Query("""
            SELECT ts FROM TrainingSession ts
            WHERE ts.userId = :userId AND LOWER(ts.skill) IN :skills
            ORDER BY ts.created DESC
            """)
    List<TrainingSession> findAllByUserIdAndLoweredSkillIn(UUID userId, Collection<String> skills);

    @Query("""
            SELECT ts FROM TrainingSession ts
            LEFT JOIN FETCH ts.questions q
            LEFT JOIN FETCH q.feedback
            WHERE ts.id = :id
            """)
    Optional<TrainingSession> findWithQuestionsById(UUID id);
}
