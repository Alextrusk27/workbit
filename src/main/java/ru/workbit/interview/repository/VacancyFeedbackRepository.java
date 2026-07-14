package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.VacancyFeedback;

import java.util.UUID;

public interface VacancyFeedbackRepository extends JpaRepository<@NotNull VacancyFeedback, @NotNull UUID> {
}
