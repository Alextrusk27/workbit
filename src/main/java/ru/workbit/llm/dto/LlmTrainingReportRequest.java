package ru.workbit.llm.dto;

import java.util.List;

public record LlmTrainingReportRequest(
        String skill,
        String profession,
        List<LlmTrainingCase> cases
) {
}
