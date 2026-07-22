package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewQuestions(
        List<String> questions
) {
}
