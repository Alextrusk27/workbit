package ru.workbit.llm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.dto.*;
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

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmGeneratedQuestions generateVacancyQuestions(LlmQuestionGenerationRequest request) {
        return llm.call("vacancy-questions-generator", request, LlmGeneratedQuestions.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingQuestion generateTrainingQuestion(LlmTrainingQuestionRequest request) {
        return llm.call("training-question-generator", request, LlmTrainingQuestion.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReport createTrainingReport(LlmTrainingReportRequest request) {
        return llm.call("training-reviewer", Map.of("JSON_STRING", request), LlmTrainingReport.class);
    }
}
