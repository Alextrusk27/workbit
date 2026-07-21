package ru.workbit.llm.dto;

public record LlmTrainingFollowUpDecision(
        boolean askFollowUp,
        String question
) {
}
