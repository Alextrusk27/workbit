package ru.workbit.llm.dto;

public record LlmTrainingAnswer(
        int index,
        String question,
        String answer
) {
}
