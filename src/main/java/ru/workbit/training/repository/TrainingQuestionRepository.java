package ru.workbit.training.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.training.model.TrainingQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingQuestionRepository extends JpaRepository<@NotNull TrainingQuestion, @NotNull UUID> {

    interface QuestionCounts {
        UUID getSessionId();

        long getTotal();

        long getAnswered();
    }

    long countByTrainingSessionIdAndAnsweredTrue(UUID trainingSessionId);

    long countByTrainingSessionId(UUID trainingSessionId);

    @Query("""
            SELECT q.trainingSession.id AS sessionId,
                   COUNT(q) AS total,
                   SUM(CASE WHEN q.answered = true THEN 1 ELSE 0 END) AS answered
            FROM TrainingQuestion q
            WHERE q.trainingSession.id IN :sessionIds
            GROUP BY q.trainingSession.id
            """)
    List<QuestionCounts> countBySessionIds(List<UUID> sessionIds);

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
}
