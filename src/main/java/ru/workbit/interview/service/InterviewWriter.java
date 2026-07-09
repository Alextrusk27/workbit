package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.QuestionResponse;
import ru.workbit.interview.dto.SessionReport;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.AnswerFeedback;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.OfferProbability;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.repository.FeedbackRepository;
import ru.workbit.interview.repository.QuestionRepository;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.dto.LlmAnswerEvaluation;
import ru.workbit.llm.dto.LlmAnswerReview;
import ru.workbit.llm.dto.LlmReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
class InterviewWriter {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final FeedbackRepository feedbackRepository;

    private final QuestionMapper questionMapper;
    private final SessionMapper sessionMapper;

    @Transactional
    public AnswerContext saveAnswer(SubmitAnswerRequest request) {
        InterviewQuestion question = questionRepository.findWithSessionById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Question not found"));

        checkQuestionOwnership(question, request.userId());
        checkQuestionSession(question, request.sessionId());
        checkSessionNotCompleted(question.getSession());
        checkQuestionNotAnswered(question);

        question.setAnswerText(request.answerText());
        question.setAnsweredAt(Instant.now());
        question.setAnswered(true);

        if (question.getSession().getStatus().equals(SessionStatus.CREATED)) {
            question.getSession().setStatus(SessionStatus.IN_PROGRESS);
        }

        return new AnswerContext(
                questionMapper.toDto(question),
                question.getSession(),
                question.getQuestionText());
    }

    @Transactional
    public QuestionResponse saveFeedback(UUID questionId, LlmAnswerEvaluation evaluation) {
        InterviewQuestion question = questionRepository.findWithSessionById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        if (!isPersistableFeedback(evaluation.score(), evaluation.feedback())) {
            log.warn("LLM returned invalid evaluation for question {} (score={}), skipping feedback",
                    questionId, evaluation.score());
            return questionMapper.toDto(question);
        }

        AnswerFeedback feedback = feedbackRepository.save(
                AnswerFeedback.builder()
                        .question(question)
                        .score(evaluation.score())
                        .feedbackText(evaluation.feedback())
                        .build()
        );
        question.setFeedback(feedback);

        return questionMapper.toDto(question);
    }

    @Transactional
    public SessionReport completeReport(UUID sessionId, LlmReport llmReport) {
        InterviewSession session = sessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        List<LlmAnswerReview> reviews = llmReport.answers() != null ? llmReport.answers() : List.of();
        Map<UUID, LlmAnswerReview> answersMap = reviews.stream()
                .collect(Collectors.toMap(LlmAnswerReview::id, Function.identity(), (a, b) -> a));

        OptionalDouble avgScore = calculateAvgScore(answersMap);
        if (avgScore.isEmpty()) {
            log.error("Cannot finish session {}: LLM report contains no usable scores", sessionId);
            throw new LlmException("Interview report has no usable scores");
        }

        saveFeedbacks(session, answersMap);
        attachReport(session, llmReport, avgScore.getAsDouble());
        markCompleted(session);

        sessionRepository.save(session);

        return sessionMapper.toSessionReport(session);
    }

    private void saveFeedbacks(InterviewSession session, Map<UUID, LlmAnswerReview> answersMap) {
        session.getQuestions().stream()
                .filter(q -> q.getFeedback() == null)
                .forEach(q -> {
                    LlmAnswerReview review = answersMap.get(q.getId());
                    if (review == null || !isPersistableFeedback(review.score(), review.evaluation())) {
                        log.warn("LLM returned no valid feedback for question {}, skipping feedback", q.getId());
                        return;
                    }
                    q.setFeedback(AnswerFeedback.builder()
                            .question(q)
                            .score(review.score())
                            .feedbackText(review.evaluation())
                            .build());
                });
    }

    private void attachReport(InterviewSession session, LlmReport llmReport, double avgScore) {
        session.setInterviewReport(InterviewReport.builder()
                .session(session)
                .avgScore(Math.round(avgScore * 10) / 10.0)
                .offerProbability(resolveOfferProbability(llmReport.offerProbability()))
                .overallFeedback(llmReport.overallFeedback())
                .build());
    }

    private OfferProbability resolveOfferProbability(String raw) {
        return OfferProbability.fromString(raw)
                .orElseThrow(() -> {
                    log.error("LLM returned unrecognized offerProbability '{}'", raw);
                    return new LlmException("Interview report has an invalid offer probability");
                });
    }

    private void markCompleted(InterviewSession session) {
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
    }

    private OptionalDouble calculateAvgScore(Map<UUID, LlmAnswerReview> answersMap) {
        return answersMap.values().stream()
                .map(LlmAnswerReview::score)
                .filter(InterviewWriter::isValidScore)
                .mapToInt(Integer::intValue)
                .average();
    }

    private void checkQuestionOwnership(InterviewQuestion question, UUID userId) {
        if (!question.getSession().getUserId().equals(userId)) {
            log.warn("IDOR attempt: user {} tried to access question {} owned by user {}",
                    userId, question.getId(), question.getSession().getUserId());
            throw new ForbiddenException("Access denied");
        }
    }

    private void checkQuestionSession(InterviewQuestion question, UUID sessionId) {
        if (!question.getSession().getId().equals(sessionId)) {
            log.warn("Question {} belongs to session {}, but request came with session {}",
                    question.getId(), question.getSession().getId(), sessionId);
            throw new ConflictException("Invalid session");
        }
    }

    private void checkSessionNotCompleted(InterviewSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            log.warn("Cannot submit answer to session {}, because it is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }

    private void checkQuestionNotAnswered(InterviewQuestion question) {
        if (question.isAnswered()) {
            throw new ConflictException("Question already answered");
        }
    }

    private static boolean isPersistableFeedback(Integer score, String feedbackText) {
        return isValidScore(score) && feedbackText != null && !feedbackText.isBlank();
    }

    private static boolean isValidScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }

    record AnswerContext(QuestionResponse response, InterviewSession session, String questionText) {
    }
}
