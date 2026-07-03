package ru.workbit.llm.dto;

import java.util.UUID;

public record LlmAnswer(
        UUID id,
        String question,
        String answer,
        String evaluation,
        Integer score
) {
}
