package ru.workbit.interview.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.workbit.interview.model.InterviewSession;

import java.util.UUID;

public interface SessionRepository extends JpaRepository<@NotNull InterviewSession, @NotNull UUID> {
}
