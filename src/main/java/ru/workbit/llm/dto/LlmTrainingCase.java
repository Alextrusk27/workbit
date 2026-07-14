package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingCase(
        int index,
        String question,
        String answer,
        List<LlmTrainingFollowUp> followUps
) {
}
