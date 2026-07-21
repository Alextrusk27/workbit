package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingQuestions(
        List<String> questions
) {
}
