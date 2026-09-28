package ru.workbit.interview.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.InterviewUserFeedback;

public interface InterviewUserFeedbackRepository extends JpaRepository<InterviewUserFeedback, UUID> {
}
