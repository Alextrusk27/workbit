package ru.workbit.training.dto;

import java.util.UUID;

public record SubmitAnswerRequest(
        UUID userId,
        UUID sessionId,
        UUID questionId,
        String answerText
) {
}
