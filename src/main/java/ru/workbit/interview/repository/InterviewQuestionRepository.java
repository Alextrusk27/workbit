package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewQuestionRepository extends JpaRepository<@NotNull InterviewQuestion, @NotNull UUID> {

    long countBySessionIdAndFollowUpFalseAndAnsweredTrue(UUID sessionId);

    long countBySessionIdAndKind(UUID sessionId, InterviewQuestion.Kind kind);

    @Query("""
            SELECT q FROM InterviewQuestion q
            JOIN FETCH q.session
            WHERE q.id = :id
            """)
    Optional<InterviewQuestion> findWithSessionById(UUID id);

    @Query("""
            SELECT q FROM InterviewQuestion q
            WHERE q.session.id = :sessionId AND q.answered = false
            ORDER BY q.followUp DESC, q.orderIndex DESC
            LIMIT 1
            """)
    Optional<InterviewQuestion> findNextUnanswered(UUID sessionId);

    List<InterviewQuestion> findAllByParentQuestionIdOrderByOrderIndex(UUID parentQuestionId);
}
