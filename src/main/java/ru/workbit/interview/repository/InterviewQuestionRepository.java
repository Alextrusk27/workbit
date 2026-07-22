package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewQuestionRepository extends JpaRepository<@NotNull InterviewQuestion, @NotNull UUID> {

    interface AnsweredCount {
        UUID getSessionId();

        long getCount();
    }

    long countBySessionIdAndFollowUpFalseAndAnsweredTrue(UUID sessionId);

    long countByParentQuestionId(UUID parentQuestionId);

    @Query("""
            SELECT q.session.id AS sessionId, COUNT(q) AS count
            FROM InterviewQuestion q
            WHERE q.session.id IN :sessionIds AND q.answered = true AND q.followUp = false
            GROUP BY q.session.id
            """)
    List<AnsweredCount> countAnsweredBySessionIds(List<UUID> sessionIds);

    @Query("""
            SELECT q FROM InterviewQuestion q
            JOIN FETCH q.session
            WHERE q.id = :id
            """)
    Optional<InterviewQuestion> findWithSessionById(UUID id);

    @Query("""
            SELECT q FROM InterviewQuestion q
            WHERE q.session.id = :sessionId AND q.answered = false AND q.followUp = true
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<InterviewQuestion> findNextUnansweredFollowUp(UUID sessionId);

    @Query("""
            SELECT q FROM InterviewQuestion q
            WHERE q.session.id = :sessionId AND q.answered = false AND q.followUp = false
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<InterviewQuestion> findNextUnansweredMain(UUID sessionId);

    @Query("""
            SELECT q FROM InterviewQuestion q
            WHERE q.session.id = :sessionId AND q.answered = true AND q.followUpChecked = false
            ORDER BY q.answeredAt DESC
            LIMIT 1
            """)
    Optional<InterviewQuestion> findLastAnsweredWithoutFollowUpCheck(UUID sessionId);

    List<InterviewQuestion> findAllByParentQuestionIdOrderByOrderIndex(UUID parentQuestionId);
}
