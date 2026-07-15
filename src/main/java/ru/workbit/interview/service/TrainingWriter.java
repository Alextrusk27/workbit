package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.TopicDictRepository;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.TrainingQuestionResponse;
import ru.workbit.interview.dto.TrainingReportResponse;
import ru.workbit.interview.dto.TrainingSessionResponse;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.TrainingFeedback;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingReport;
import ru.workbit.interview.model.TrainingSession;
import ru.workbit.interview.model.mapper.TrainingQuestionMapper;
import ru.workbit.interview.model.mapper.TrainingReportMapper;
import ru.workbit.interview.model.mapper.TrainingSessionMapper;
import ru.workbit.interview.repository.TrainingQuestionRepository;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
            questions.add(mainQuestion(session, bankQuestion.getText(), bankQuestion.getId(), questions.size() + 1));
        }
        for (String questionText : generatedQuestions) {
            questions.add(mainQuestion(session, questionText, null, questions.size() + 1));
        }

        session.setQuestions(questions);
        trainingSessionRepository.save(session);

        return trainingSessionMapper.toResponse(session, 0);
    }

    private static TrainingQuestion mainQuestion(TrainingSession session, String questionText, UUID bankQuestionId,
                                                 int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .questionText(questionText)
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
    public TrainingQuestionResponse saveFollowUp(UUID answeredQuestionId, UUID caseMainId, String questionText) {
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
                .questionText(questionText)
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

        List<TrainingQuestion> answered = session.getQuestions().stream()
                .filter(TrainingQuestion::isAnswered)
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .toList();

        List<List<TrainingQuestion>> cases = groupCases(answered);
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
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());

        trainingSessionRepository.save(session);

        return trainingReportMapper.toResponse(session.getReport(), session, mains);
    }

    static List<List<TrainingQuestion>> groupCases(List<TrainingQuestion> answered) {
        Map<UUID, List<TrainingQuestion>> followUpsByParent = answered.stream()
                .filter(TrainingQuestion::isFollowUp)
                .collect(Collectors.groupingBy(TrainingQuestion::getParentQuestionId));

        return answered.stream()
                .filter(q -> !q.isFollowUp())
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .map(main -> {
                    List<TrainingQuestion> trainingCase = new ArrayList<>();
                    trainingCase.add(main);
                    followUpsByParent.getOrDefault(main.getId(), List.of()).stream()
                            .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                            .forEach(trainingCase::add);
                    return trainingCase;
                })
                .toList();
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
                    .feedbackText(review.evaluation())
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

    private void checkSessionNotCompleted(TrainingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            log.warn("Session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }

    private static boolean isPersistableFeedback(Integer score, String feedbackText) {
        return score != null && score >= 1 && score <= 5 && feedbackText != null && !feedbackText.isBlank();
    }
}
