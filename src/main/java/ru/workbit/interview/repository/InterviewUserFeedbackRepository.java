package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.InterviewUserFeedback;

import java.util.UUID;

public interface InterviewUserFeedbackRepository extends JpaRepository<@NotNull InterviewUserFeedback, @NotNull UUID> {
}
