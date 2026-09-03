package ru.workbit.training.repository;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.training.model.TrainingFeedback;

public interface TrainingFeedbackRepository extends JpaRepository<@NotNull TrainingFeedback, @NotNull UUID> {
}
