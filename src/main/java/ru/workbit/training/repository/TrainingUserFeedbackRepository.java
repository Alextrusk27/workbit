package ru.workbit.training.repository;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.training.model.TrainingUserFeedback;

public interface TrainingUserFeedbackRepository extends JpaRepository<@NotNull TrainingUserFeedback, @NotNull UUID> {
}
