package ru.workbit.llm.dto;

import java.util.List;

public record LlmReportRequest(
        String profession,
        String level,
        List<LlmAnswer> answers
) {
}
