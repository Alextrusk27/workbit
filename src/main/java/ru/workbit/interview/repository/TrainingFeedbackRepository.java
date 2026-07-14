package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.TrainingFeedback;

import java.util.UUID;

public interface TrainingFeedbackRepository extends JpaRepository<@NotNull TrainingFeedback, @NotNull UUID> {
}
