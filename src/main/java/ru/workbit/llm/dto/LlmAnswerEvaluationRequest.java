package ru.workbit.llm.dto;

public record LlmAnswerEvaluationRequest(
        String profession,
        String question,
        String level,
        String answer
) {
}
