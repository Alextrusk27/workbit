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
    public LlmTrainingQuestions generateTrainingQuestions(LlmTrainingQuestionsRequest request) {
        return llm.call("training-question-generator", request, LlmTrainingQuestions.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingFollowUpDecision decideTrainingFollowUp(LlmTrainingFollowUpRequest request) {
        return llm.call("training-follow-up", request, LlmTrainingFollowUpDecision.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReport createTrainingReport(LlmTrainingReportRequest request) {
        return llm.call("training-reviewer", Map.of("JSON_STRING", request), LlmTrainingReport.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInputNormalization normalizeInput(LlmInputNormalizationRequest request) {
        return llm.call("input-normalizer", request, LlmInputNormalization.class);
    }
}
