package ru.workbit.llm.dto;

public record AnswerEvaluationRequest(
        String profession,
        String question,
        String level,
        String answer
) {
}
