package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingReport(
        List<LlmTrainingAnswerReview> answers,
        String overallFeedback
) {
}
