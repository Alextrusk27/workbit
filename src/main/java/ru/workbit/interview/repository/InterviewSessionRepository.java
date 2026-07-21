package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<@NotNull InterviewSession, @NotNull UUID> {

    boolean existsByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    List<InterviewSession> findAllByUserId(@NotNull UUID userId);

    Optional<InterviewSession> findByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    @Query("""
            SELECT s FROM InterviewSession s
            JOIN FETCH s.questions q
            LEFT JOIN FETCH q.feedback
            WHERE s.id = :id
            """)
    Optional<InterviewSession> findWithQuestionsById(@NotNull UUID id);

    @Modifying
    @Query("DELETE FROM InterviewSession s WHERE s.userId = :userId")
    void deleteAllByUserId(@NotNull UUID userId);
}
