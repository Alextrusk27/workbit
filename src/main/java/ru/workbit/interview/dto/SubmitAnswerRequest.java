package ru.workbit.interview.dto;

import java.util.UUID;

public record SubmitAnswerRequest(
        UUID userId,
        UUID sessionId,
        UUID questionId,
        boolean evaluate,
        String answerText
) {
}
