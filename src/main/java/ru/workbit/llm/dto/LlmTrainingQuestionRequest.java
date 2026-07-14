package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingQuestionRequest(
        String profession,
        String level,
        String companyType,
        List<LlmTrainingHistoryItem> history,
        int questionNumber,
        boolean allowFollowUp
) {
}
