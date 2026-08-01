package ru.workbit.training.dto;

import ru.workbit.training.model.TrainingSession;

import java.util.UUID;

public record TrainingTopicMatch(
        UUID sessionId,
        String topic,
        TrainingSession.Status status,
        Double avgScore,
        int answeredCount,
        int totalQuestions
) {
}
