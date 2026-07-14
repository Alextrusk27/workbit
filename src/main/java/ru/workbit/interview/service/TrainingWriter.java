package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.TrainingQuestionResponse;
import ru.workbit.interview.dto.TrainingReportResponse;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.TrainingFeedback;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingReport;
import ru.workbit.interview.model.TrainingSession;
import ru.workbit.interview.model.mapper.TrainingQuestionMapper;
import ru.workbit.interview.model.mapper.TrainingReportMapper;
import ru.workbit.interview.repository.TrainingQuestionRepository;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingAnswerReview;
import ru.workbit.llm.dto.LlmTrainingReport;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
class TrainingWriter {

    private static final int MIN_OVERALL_FEEDBACK_LENGTH = 10;

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingQuestionRepository trainingQuestionRepository;

    private final TrainingQuestionMapper trainingQuestionMapper;
    private final TrainingReportMapper trainingReportMapper;

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

        saveFeedbacks(answered, llmReport.answers() != null ? llmReport.answers() : List.of());

        double avgScore = calculateAvgScore(sessionId, answered);

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

    private void saveFeedbacks(List<TrainingQuestion> answered, List<LlmTrainingAnswerReview> reviews) {
        for (LlmTrainingAnswerReview review : reviews) {
            if (review.index() < 1 || review.index() > answered.size()) {
                log.warn("LLM returned review with invalid index {}, skipping feedback", review.index());
                continue;
            }
            if (!isPersistableFeedback(review.score(), review.evaluation())) {
                log.warn("LLM returned invalid review for answer {} (score={}), skipping feedback",
                        review.index(), review.score());
                continue;
            }

            TrainingQuestion question = answered.get(review.index() - 1);
            if (question.getFeedback() != null) {
                continue;
            }
            question.setFeedback(TrainingFeedback.builder()
                    .question(question)
                    .score(review.score())
                    .feedbackText(review.evaluation())
                    .build());
        }
    }

    private double calculateAvgScore(UUID sessionId, List<TrainingQuestion> answered) {
        OptionalDouble avg = answered.stream()
                .map(TrainingQuestion::getFeedback)
                .filter(Objects::nonNull)
                .mapToInt(TrainingFeedback::getScore)
                .average();

        if (avg.isEmpty()) {
            log.error("Cannot finish session {}: LLM report contains no usable scores", sessionId);
            throw new LlmException("Training report has no usable scores");
        }

        return Math.round(avg.getAsDouble() * 10) / 10.0;
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
