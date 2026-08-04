package ru.workbit.llm.dto;

public record LlmTrainingCase(
        int index,
        String question,
        String answer
) {
}
