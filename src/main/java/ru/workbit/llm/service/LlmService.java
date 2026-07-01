package ru.workbit.llm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.dto.LlmAnswerEvaluation;
import ru.workbit.llm.dto.LlmAnswerEvaluationRequest;
import ru.workbit.llm.dto.LlmReport;
import ru.workbit.llm.dto.LlmReportRequest;
import ru.workbit.util.annotation.Loggable;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmService {
    private final LlmClient llm;

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmAnswerEvaluation evaluateAnswer(LlmAnswerEvaluationRequest request) {
        return llm.call("answer-evaluator", request, LlmAnswerEvaluation.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmReport createReport(LlmReportRequest request) {
        return llm.call("interview-reviewer", Map.of("JSON_STRING", request), LlmReport.class);
    }
}
