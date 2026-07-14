package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.workbit.interview.model.VacancySession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacancySessionRepository extends JpaRepository<@NotNull VacancySession, @NotNull UUID> {

    boolean existsByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    List<VacancySession> findAllByUserId(@NotNull UUID userId);

    Optional<VacancySession> findByIdAndUserId(@NotNull UUID id, @NotNull UUID userId);

    @Query("""
            SELECT vs FROM VacancySession vs
            JOIN FETCH vs.questions q
            LEFT JOIN FETCH q.feedback
            WHERE vs.id = :id
            """)
    Optional<VacancySession> findWithQuestionsById(@NotNull UUID id);
}
