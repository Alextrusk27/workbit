package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewReport(
        List<LlmInterviewAnswerReview> answers,
        LlmOfferProbability offerProbability,
        String overallFeedback,
        String recommendations,
        String weakestSkill
) {
}
