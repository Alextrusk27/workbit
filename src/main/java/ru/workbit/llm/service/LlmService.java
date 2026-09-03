package ru.workbit.llm.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.llm.client.InterviewerClient;
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.client.ReviewerClient;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmInterviewAnswer;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.util.annotation.Loggable;

@Service
@RequiredArgsConstructor
public class LlmService {
    private final LlmClient llm;
    private final InterviewerClient interviewer;
    private final ReviewerClient reviewer;

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingQuestions generateTrainingQuestions(String grade, LlmTrainingQuestionsRequest request) {
        return llm.call("training-question-generator-" + grade, request, LlmTrainingQuestions.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReport createTrainingReport(LlmTrainingReportRequest request) {
        return llm.call("training-reviewer", Map.of("JSON_STRING", request), LlmTrainingReport.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReferenceAnswer createReferenceAnswer(LlmTrainingReferenceAnswerRequest request) {
        return llm.call("training-reference-answer", request, LlmTrainingReferenceAnswer.class);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewPlan planInterview(LlmInterviewVacancy vacancy) {
        return interviewer.plan(vacancy);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewStep nextInterviewStep(LlmInterviewVacancy vacancy, LlmInterviewPlan plan,
                                              List<LlmInterviewTurn> history, String lastAnswer) {
        return interviewer.next(vacancy, plan, history, lastAnswer);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewReport createInterviewReport(LlmInterviewVacancy vacancy, List<LlmInterviewAnswer> answers) {
        return reviewer.review(vacancy, answers);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInputNormalization normalizeInput(LlmInputNormalizationRequest request) {
        return llm.call("input-normalizer", request, LlmInputNormalization.class);
    }
}
