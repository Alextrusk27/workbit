package ru.workbit.interview.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewSession;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    @EntityGraph(attributePaths = "report")
    List<InterviewSession> findAllByUserIdOrderByCreatedDesc(UUID userId);

    Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "report")
    List<InterviewSession> findAllByUserIdAndVacancySnapshotIdInOrderByCreatedAsc(
            UUID userId, Collection<UUID> vacancySnapshotIds);

    boolean existsByUserIdAndVacancySnapshotIdInAndStatusNot(
            UUID userId, Collection<UUID> vacancySnapshotIds,
            InterviewSession.Status status);

    @Query("""
            SELECT s FROM InterviewSession s
            JOIN FETCH s.questions q
            LEFT JOIN FETCH q.feedback
            WHERE s.id = :id
            """)
    Optional<InterviewSession> findWithQuestionsById(UUID id);
}
