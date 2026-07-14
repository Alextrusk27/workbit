package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingReportRequest(
        String profession,
        String level,
        List<LlmTrainingAnswer> answers
) {
}
