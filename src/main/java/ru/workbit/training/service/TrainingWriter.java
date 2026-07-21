package ru.workbit.training.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.TopicDictRepository;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingFeedback;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.mapper.TrainingQuestionMapper;
import ru.workbit.training.model.mapper.TrainingReportMapper;
import ru.workbit.training.model.mapper.TrainingSessionMapper;
import ru.workbit.training.repository.TrainingQuestionRepository;
import ru.workbit.training.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static ru.workbit.training.service.TrainingCases.answeredSorted;
import static ru.workbit.training.service.TrainingCases.checkSessionNotCompleted;
import static ru.workbit.training.service.TrainingCases.groupCases;

@Component
@Slf4j
@RequiredArgsConstructor
class TrainingWriter {

    private static final int MIN_OVERALL_FEEDBACK_LENGTH = 10;
    private static final double MIN_REVIEWED_CASES_RATIO = 0.5;

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingQuestionRepository trainingQuestionRepository;
    private final ProfessionDictRepository professionDictRepository;
    private final TopicDictRepository topicDictRepository;

    private final TrainingSessionMapper trainingSessionMapper;
    private final TrainingQuestionMapper trainingQuestionMapper;
    private final TrainingReportMapper trainingReportMapper;

    record DictionaryRefs(UUID professionId, UUID topicId) {
    }

    @Transactional
    public DictionaryRefs upsertDictionaries(String profession, String topic) {
        UUID professionId = professionDictRepository.upsertAndIncrementUsage(profession);
        UUID topicId = topic != null ? topicDictRepository.upsertAndIncrementUsage(professionId, topic) : null;
        return new DictionaryRefs(professionId, topicId);
    }

    @Transactional
    public TrainingSessionResponse createSession(TrainingSession session, List<BankQuestion> bankQuestions,
                                                 List<String> generatedQuestions) {
        List<TrainingQuestion> questions = new ArrayList<>();
        for (BankQuestion bankQuestion : bankQuestions) {
            questions.add(buildMainQuestion(session, bankQuestion.getText(), bankQuestion.getId(),
                    questions.size() + 1));
        }
        for (String text : generatedQuestions) {
            questions.add(buildMainQuestion(session, text, null, questions.size() + 1));
        }

        session.setQuestions(questions);
        trainingSessionRepository.save(session);

        return trainingSessionMapper.toResponse(session, 0);
    }

    private static TrainingQuestion buildMainQuestion(TrainingSession session, String text,
                                                      UUID bankQuestionId, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .text(text)
                .orderIndex(orderIndex)
                .build();
    }

    @Transactional
    public void markFollowUpChecked(UUID questionId) {
        trainingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"))
                .setFollowUpChecked(true);
    }

    @Transactional
    public TrainingQuestionResponse saveFollowUp(UUID answeredQuestionId, UUID caseMainId, String text) {
        TrainingQuestion answered = trainingQuestionRepository.findWithSessionById(answeredQuestionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        TrainingSession session = answered.getTrainingSession();
        checkSessionNotCompleted(session);
        answered.setFollowUpChecked(true);

        Optional<TrainingQuestion> pending = trainingQuestionRepository.findNextUnansweredFollowUp(session.getId());
        if (pending.isPresent()) {
            log.warn("Session {} already has an unanswered follow-up, discarding the generated one", session.getId());
            return trainingQuestionMapper.toDto(pending.get());
        }

        TrainingQuestion followUp = trainingQuestionRepository.save(TrainingQuestion.builder()
                .trainingSession(session)
                .parentQuestionId(caseMainId)
                .text(text)
                .orderIndex((int) trainingQuestionRepository.countByParentQuestionId(caseMainId) + 1)
                .followUp(true)
                .build());

        return trainingQuestionMapper.toDto(followUp);
    }

    @Transactional
    public TrainingReportResponse completeReport(UUID sessionId, LlmTrainingReport llmReport) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);
        checkOverallFeedback(sessionId, llmReport.overallFeedback());

        List<List<TrainingQuestion>> cases = groupCases(answeredSorted(session));
        saveFeedbacks(cases, llmReport.cases() != null ? llmReport.cases() : List.of());
        checkEnoughReviewedCases(sessionId, cases);

        List<TrainingQuestion> mains = cases.stream().map(List::getFirst).toList();
        double avgScore = calculateAvgScore(mains);

        session.getQuestions().removeIf(q -> !q.isAnswered() || q.isFollowUp());
        session.setReport(TrainingReport.builder()
                .trainingSession(session)
                .avgScore(avgScore)
                .overallFeedback(llmReport.overallFeedback())
                .build());
        session.setStatus(TrainingSession.Status.COMPLETED);
        session.setCompletedAt(Instant.now());

        trainingSessionRepository.save(session);

        return trainingReportMapper.toResponse(session.getReport(), session, mains);
    }

    private void saveFeedbacks(List<List<TrainingQuestion>> cases, List<LlmTrainingCaseReview> reviews) {
        for (LlmTrainingCaseReview review : reviews) {
            if (review.index() < 1 || review.index() > cases.size()) {
                log.warn("LLM returned review with invalid index {}, skipping feedback", review.index());
                continue;
            }
            if (!isPersistableFeedback(review.score(), review.evaluation())) {
                log.warn("LLM returned invalid review for case {} (score={}), skipping feedback",
                        review.index(), review.score());
                continue;
            }

            TrainingQuestion question = cases.get(review.index() - 1).getFirst();
            if (question.getFeedback() != null) {
                log.warn("LLM returned duplicate review for case {}, skipping feedback", review.index());
                continue;
            }
            question.setFeedback(TrainingFeedback.builder()
                    .question(question)
                    .score(review.score())
                    .text(review.evaluation())
                    .build());
        }
    }

    private double calculateAvgScore(List<TrainingQuestion> mains) {
        double avg = mains.stream()
                .map(TrainingQuestion::getFeedback)
                .filter(Objects::nonNull)
                .mapToInt(TrainingFeedback::getScore)
                .average()
                .orElseThrow(() -> new LlmException("Training report has no usable scores"));
        return Math.round(avg * 10) / 10.0;
    }

    private void checkEnoughReviewedCases(UUID sessionId, List<List<TrainingQuestion>> cases) {
        long reviewed = cases.stream().filter(c -> c.getFirst().getFeedback() != null).count();
        if (reviewed < cases.size() * MIN_REVIEWED_CASES_RATIO) {
            log.error("Cannot finish session {}: LLM reviewed only {} of {} cases", sessionId, reviewed, cases.size());
            throw new LlmException("Training report has too few reviewed cases");
        }
    }

    private void checkOverallFeedback(UUID sessionId, String overallFeedback) {
        if (overallFeedback == null || overallFeedback.isBlank()
                || overallFeedback.length() < MIN_OVERALL_FEEDBACK_LENGTH) {
            log.error("Cannot finish session {}: LLM report has no usable overall feedback", sessionId);
            throw new LlmException("Training report has no usable overall feedback");
        }
    }

    private static boolean isPersistableFeedback(Integer score, String text) {
        return score != null && score >= 1 && score <= 5 && text != null && !text.isBlank();
    }
}
