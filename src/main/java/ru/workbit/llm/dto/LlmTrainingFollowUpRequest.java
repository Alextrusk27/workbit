package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingFollowUpRequest(
        String profession,
        String level,
        String question,
        String answer,
        List<LlmTrainingFollowUp> previousFollowUps
) {
}
