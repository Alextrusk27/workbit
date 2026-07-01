package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.AnswerFeedback;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<@NotNull AnswerFeedback, @NotNull UUID> {
}
