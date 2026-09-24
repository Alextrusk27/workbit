package ru.workbit.interview.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;

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
            ORDER BY q.followUp DESC, q.orderIndex
            LIMIT 1
            """)
    Optional<InterviewQuestion> findNextUnanswered(UUID sessionId);

    long countByParentQuestionId(UUID parentQuestionId);

    /** Тексты основных вопросов, уже заданных этому пользователю в завершённых интервью по этой вакансии. */
    @Query("""
            SELECT q.text FROM InterviewQuestion q
            WHERE q.session.userId = :userId
              AND q.session.vacancySnapshotId IN :vacancySnapshotIds
              AND q.session.status = :status
              AND q.kind = :kind
            ORDER BY q.session.created, q.orderIndex
            """)
    List<String> findQuestionTexts(UUID userId, Collection<UUID> vacancySnapshotIds,
                                   InterviewSession.Status status, InterviewQuestion.Kind kind);
}
