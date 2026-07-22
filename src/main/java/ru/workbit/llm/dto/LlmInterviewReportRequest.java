package ru.workbit.llm.dto;

import java.util.List;

public record LlmInterviewReportRequest(
        String vacancyName,
        String experience,
        List<LlmInterviewAnswer> answers
) {
}
