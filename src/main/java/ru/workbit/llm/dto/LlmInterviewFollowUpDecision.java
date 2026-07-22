package ru.workbit.llm.dto;

public record LlmInterviewFollowUpDecision(
        boolean askFollowUp,
        String question
) {
}
