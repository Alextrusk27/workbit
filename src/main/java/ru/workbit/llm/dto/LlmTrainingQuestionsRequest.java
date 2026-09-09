package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingQuestionsRequest(
        String skill,
        String profession,
        String level,
        int count,
        List<String> existingQuestions
) {
}
