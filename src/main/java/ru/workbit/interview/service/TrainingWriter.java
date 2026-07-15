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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    public TrainingQuestionResponse saveQuestion(UUID sessionId, String questionText, boolean followUp) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        Optional<TrainingQuestion> unanswered = trainingQuestionRepository.findNextUnanswered(sessionId);
        if (unanswered.isPresent()) {
            log.warn("Session {} already has an unanswered question, discarding the generated one", sessionId);
            return trainingQuestionMapper.toDto(unanswered.get());
        }

        TrainingQuestion question = trainingQuestionRepository.save(TrainingQuestion.builder()
                .trainingSession(session)
                .questionText(questionText)
                .orderIndex((int) trainingQuestionRepository.countByTrainingSessionId(sessionId) + 1)
                .followUp(followUp)
                .build());

        return trainingQuestionMapper.toDto(question);
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

        double avgScore = calculateAvgScore(answered);

        session.getQuestions().removeIf(q -> !q.isAnswered());
        session.setReport(TrainingReport.builder()
                .trainingSession(session)
                .avgScore(avgScore)
                .overallFeedback(llmReport.overallFeedback())
                .build());
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());

        trainingSessionRepository.save(session);

        return trainingReportMapper.toResponse(session.getReport(), session, answered);
    }

    static List<List<TrainingQuestion>> groupCases(List<TrainingQuestion> answered) {
        List<List<TrainingQuestion>> cases = new ArrayList<>();
        for (TrainingQuestion question : answered) {
            if (!question.isFollowUp() || cases.isEmpty()) {
                cases.add(new ArrayList<>());
            }
            cases.getLast().add(question);
        }
        return cases;
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

    private double calculateAvgScore(List<TrainingQuestion> answered) {
        double avg = answered.stream()
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
