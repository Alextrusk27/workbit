package ru.workbit.training.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.training.model.TrainingFeedback;

import java.util.UUID;

public interface TrainingFeedbackRepository extends JpaRepository<@NotNull TrainingFeedback, @NotNull UUID> {
}
