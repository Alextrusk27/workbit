package ru.workbit.training.dto;

import java.util.UUID;
import ru.workbit.training.model.TrainingSession;

public record TrainingSkillMatch(
        UUID sessionId,
        String skill,
        TrainingSession.Status status,
        Double avgScore,
        int answeredCount,
        int totalQuestions
) {
}
