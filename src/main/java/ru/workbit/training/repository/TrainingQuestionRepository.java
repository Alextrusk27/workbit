package ru.workbit.training.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.training.model.TrainingQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingQuestionRepository extends JpaRepository<@NotNull TrainingQuestion, @NotNull UUID> {

    interface AnsweredCount {
        UUID getSessionId();

        long getCount();
    }

    long countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(UUID trainingSessionId);

    long countByParentQuestionId(UUID parentQuestionId);

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
            WHERE q.trainingSession.id = :sessionId AND q.answered = false AND q.followUp = true
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<TrainingQuestion> findNextUnansweredFollowUp(UUID sessionId);

    @Query("""
            SELECT q FROM TrainingQuestion q
            WHERE q.trainingSession.id = :sessionId AND q.answered = false AND q.followUp = false
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<TrainingQuestion> findNextUnansweredMain(UUID sessionId);

    @Query("""
            SELECT q FROM TrainingQuestion q
            WHERE q.trainingSession.id = :sessionId AND q.answered = true AND q.followUpChecked = false
            ORDER BY q.answeredAt DESC
            LIMIT 1
            """)
    Optional<TrainingQuestion> findLastAnsweredWithoutFollowUpCheck(UUID sessionId);

    List<TrainingQuestion> findAllByParentQuestionIdOrderByOrderIndex(UUID parentQuestionId);
}
