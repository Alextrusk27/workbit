package ru.workbit.llm.dto;

import java.util.List;

public record LlmReport(
        List<LlmAnswerReview> answers,
        String overallFeedback,
        String offerProbability
) {
}
