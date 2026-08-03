package ru.workbit.llm.dto;

public record LlmTrainingReferenceAnswerRequest(
        String skill,
        String profession,
        String question
) {
}
