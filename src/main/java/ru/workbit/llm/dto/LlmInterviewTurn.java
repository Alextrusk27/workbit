package ru.workbit.llm.dto;

public record LlmInterviewTurn(
        String candidateAnswer,
        LlmInterviewStep reply
) {
}
