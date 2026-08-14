package ru.workbit.training.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.training.model.TrainingUserFeedback;

import java.util.UUID;

public interface TrainingUserFeedbackRepository extends JpaRepository<@NotNull TrainingUserFeedback, @NotNull UUID> {
}
