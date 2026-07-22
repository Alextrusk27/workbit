package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewQuestionsRequest(
        String name,
        String employer,
        String experience,
        List<String> keySkills,
        String description,
        int minCount,
        int maxCount
) {
    public static final int MIN_COUNT = 5;
    public static final int MAX_COUNT = 20;
}
