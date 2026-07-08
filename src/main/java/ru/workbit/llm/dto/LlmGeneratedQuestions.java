package ru.workbit.llm.dto;

import java.util.List;

public record LlmGeneratedQuestions(
        String title,
        List<String> questions
) {
}
