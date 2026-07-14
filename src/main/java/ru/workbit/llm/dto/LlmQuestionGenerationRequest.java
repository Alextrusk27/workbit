package ru.workbit.llm.dto;

public record LlmQuestionGenerationRequest(
        String vacancyName,
        String employer,
        String experience,
        String keySkills,
        String description,
        Integer questionCount
) {
}
