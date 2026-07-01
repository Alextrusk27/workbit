package ru.workbit.interview.dto;

import java.util.UUID;

public record QuestionRequest(
        UUID sessionId,
        int index,
        UUID userId
) {
}
