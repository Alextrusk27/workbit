package ru.workbit.llm.dto;

public record LlmTrainingHistoryItem(
        String question,
        String answer,
        boolean followUp
) {
}
