package ru.workbit.llm.dto;

public record LlmTrainingAnswerReview(
        int index,
        String evaluation,
        Integer score
) {
}
