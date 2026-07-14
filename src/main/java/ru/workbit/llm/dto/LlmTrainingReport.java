package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingReport(
        List<LlmTrainingCaseReview> cases,
        String overallFeedback
) {
}
