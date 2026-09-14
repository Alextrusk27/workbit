package ru.workbit.interview.repository;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.InterviewFeedback;

public interface InterviewFeedbackRepository extends JpaRepository<@NotNull InterviewFeedback, @NotNull UUID> {
}
