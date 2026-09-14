package ru.workbit.llm.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.llm.client.InterviewerClient;
import ru.workbit.llm.client.NormalizerClient;
import ru.workbit.llm.client.QuestionGeneratorClient;
import ru.workbit.llm.client.ReferenceAnswerClient;
import ru.workbit.llm.client.ReviewerClient;
import ru.workbit.llm.client.TrainingReviewerClient;
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
    private final InterviewerClient interviewer;
    private final ReviewerClient reviewer;
    private final NormalizerClient normalizer;
    private final QuestionGeneratorClient questionGenerator;
    private final ReferenceAnswerClient referenceAnswer;
    private final TrainingReviewerClient trainingReviewer;

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingQuestions generateTrainingQuestions(LlmTrainingQuestionsRequest request) {
        return questionGenerator.generate(request);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReport createTrainingReport(LlmTrainingReportRequest request) {
        return trainingReviewer.review(request);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmTrainingReferenceAnswer createReferenceAnswer(LlmTrainingReferenceAnswerRequest request) {
        return referenceAnswer.create(request);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewPlan planInterview(LlmInterviewVacancy vacancy, String askedBefore) {
        return interviewer.plan(vacancy, askedBefore);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewStep nextInterviewStep(LlmInterviewVacancy vacancy, LlmInterviewPlan plan,
                                              List<LlmInterviewTurn> history, String lastAnswer,
                                              String askedBefore) {
        return interviewer.next(vacancy, plan, history, lastAnswer, askedBefore);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInterviewReport createInterviewReport(LlmInterviewVacancy vacancy, List<LlmInterviewAnswer> answers) {
        return reviewer.review(vacancy, answers);
    }

    @Loggable(level = "DEBUG", logArgs = true, logResult = true)
    public LlmInputNormalization normalizeInput(LlmInputNormalizationRequest request) {
        return normalizer.normalize(request);
    }
}
