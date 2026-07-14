package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.TrainingQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingQuestionRepository extends JpaRepository<@NotNull TrainingQuestion, @NotNull UUID> {

    interface AnsweredCount {
        UUID getSessionId();

        long getCount();
    }

    long countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(UUID trainingSessionId);

    long countByTrainingSessionId(UUID trainingSessionId);

    @Query("""
            SELECT q.trainingSession.id AS sessionId, COUNT(q) AS count
            FROM TrainingQuestion q
            WHERE q.trainingSession.id IN :sessionIds AND q.answered = true AND q.followUp = false
            GROUP BY q.trainingSession.id
            """)
    List<AnsweredCount> countAnsweredBySessionIds(List<UUID> sessionIds);

    @Query("""
            SELECT q FROM TrainingQuestion q
            JOIN FETCH q.trainingSession
            WHERE q.id = :id
            """)
    Optional<TrainingQuestion> findWithSessionById(UUID id);

    @Query("""
            SELECT q FROM TrainingQuestion q
            WHERE q.trainingSession.id = :sessionId AND q.answered = false
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<TrainingQuestion> findNextUnanswered(UUID sessionId);

    List<TrainingQuestion> findAllByTrainingSessionIdOrderByOrderIndex(UUID trainingSessionId);
}
