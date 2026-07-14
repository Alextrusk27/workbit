package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.VacancyQuestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacancyQuestionRepository extends JpaRepository<@NotNull VacancyQuestion, @NotNull UUID> {

    interface AnsweredCount {
        UUID getSessionId();

        long getCount();
    }

    Optional<VacancyQuestion> findByVacancySessionIdAndOrderIndex(UUID vacancySessionId, int orderIndex);

    long countByVacancySessionIdAndAnsweredTrue(UUID vacancySessionId);

    @Query("""
            SELECT q.vacancySession.id AS sessionId, COUNT(q) AS count
            FROM VacancyQuestion q
            WHERE q.vacancySession.id IN :sessionIds AND q.answered = true
            GROUP BY q.vacancySession.id
            """)
    List<AnsweredCount> countAnsweredBySessionIds(List<UUID> sessionIds);

    @Query("""
            SELECT q FROM VacancyQuestion q
            JOIN FETCH q.vacancySession
            WHERE q.id = :id
            """)
    Optional<VacancyQuestion> findWithSessionById(UUID id);

    @Query("""
            SELECT q FROM VacancyQuestion q
            WHERE q.vacancySession.id = :sessionId AND q.answered = false
            ORDER BY q.orderIndex
            LIMIT 1
            """)
    Optional<VacancyQuestion> findNextUnanswered(UUID sessionId);
}
