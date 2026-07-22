package ru.workbit.llm.dto;

public record LlmInterviewAnswerReview(
        int index,
        String evaluation,
        Integer score
) {
}
