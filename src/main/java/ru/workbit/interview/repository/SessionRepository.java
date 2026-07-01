package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<@NotNull InterviewSession, @NotNull UUID> {

    boolean existsByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    List<InterviewSession> findAllByUserId(@NotNull UUID userId);

    Optional<InterviewSession> findByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    @Query("""
            SELECT is FROM InterviewSession is
            JOIN FETCH is.questions isq
            LEFT JOIN FETCH isq.feedback
            WHERE is.id = :id
            """)
    Optional<InterviewSession> findWithQuestionsById(@NotNull UUID id);
}
