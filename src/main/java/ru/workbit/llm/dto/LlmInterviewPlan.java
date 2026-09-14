package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewPlan(
        int questionCount,
        List<LlmInterviewTopic> topics,
        String topic,
        String question
) {
    public static final int MIN_COUNT = 5;
    public static final int MAX_COUNT = 12;
}
