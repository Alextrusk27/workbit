package ru.workbit.llm.dto;

public record LlmInterviewFollowUp(
        LlmInterviewStepKind kind,
        String question,
        String answer
) {
}
