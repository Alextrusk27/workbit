package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewFollowUpRequest(
        String vacancyName,
        String question,
        String answer,
        List<LlmInterviewFollowUp> previousFollowUps
) {
}
