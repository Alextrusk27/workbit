package ru.workbit.training.dto;

import ru.workbit.training.model.TrainingSession;

import java.util.UUID;

public record TrainingSkillMatch(
        UUID sessionId,
        String skill,
        TrainingSession.Status status,
        Double avgScore,
        int answeredCount,
        int totalQuestions
) {
}
