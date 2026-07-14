package ru.workbit.llm.dto;

public record LlmTrainingCaseReview(
        int index,
        String evaluation,
        Integer score
) {
}
