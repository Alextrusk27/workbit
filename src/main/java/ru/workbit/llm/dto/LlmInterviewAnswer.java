package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewAnswer(
        int index,
        String question,
        String answer,
        List<LlmInterviewFollowUp> followUps
) {
}
