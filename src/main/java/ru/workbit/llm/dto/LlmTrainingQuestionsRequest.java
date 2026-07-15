package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingQuestionsRequest(
        String profession,
        String topic,
        String level,
        int count,
        List<String> existingQuestions
) {
}
