package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewReport(
        List<LlmInterviewAnswerReview> answers,
        String offerProbability,
        String overallFeedback,
        String recommendations,
        String weakestSkill
) {
}
