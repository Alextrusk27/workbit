package ru.workbit.llm.dto;

import java.util.UUID;

public record LlmAnswerReview(
        UUID id,
        String evaluation,
        Integer score
) {
}
