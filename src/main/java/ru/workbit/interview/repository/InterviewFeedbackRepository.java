package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.InterviewFeedback;

import java.util.UUID;

public interface InterviewFeedbackRepository extends JpaRepository<@NotNull InterviewFeedback, @NotNull UUID> {
}
