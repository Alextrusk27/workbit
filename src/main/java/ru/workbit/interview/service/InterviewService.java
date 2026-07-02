package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.InternalServerException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.*;
import ru.workbit.interview.model.*;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.question.BankQuestion;
import ru.workbit.interview.question.QuestionBank;
import ru.workbit.interview.repository.FeedbackRepository;
import ru.workbit.interview.repository.QuestionRepository;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.dto.*;
import ru.workbit.llm.service.LlmService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {
    private final QuestionBank questionBank;
    private final LlmService llmService;

    private final SessionMapper sessionMapper;
    private final QuestionMapper questionMapper;

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final FeedbackRepository feedbackRepository;

    public InterviewOptionsResponse getOptions() {
        return new InterviewOptionsResponse(
                List.of(Profession.values()),
                List.of(Level.values()),
                List.of(CompanyType.values()),
                CreateSessionRequest.MIN_QUESTIONS,
                CreateSessionRequest.MAX_QUESTIONS
        );
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, UUID userId) {

        InterviewSession session = sessionMapper.toEntity(request);
        session.setUserId(userId);
        session.setQuestions(createQuestions(request, session));
        sessionRepository.save(session);

        return sessionMapper.toResponse(session, 0);
    }

    public List<SessionResponse> getAllSessions(UUID userId) {
        List<InterviewSession> sessions = sessionRepository.findAllByUserId(userId);

        Map<UUID, Long> answered = questionRepository
                .countAnsweredBySessionIds(sessions.stream().map(InterviewSession::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        QuestionRepository.AnsweredCount::getSessionId,
                        QuestionRepository.AnsweredCount::getCount));

        return sessions.stream()
                .map(session -> sessionMapper.toResponse(
                        session, answered.getOrDefault(session.getId(), 0L).intValue()))
                .toList();
    }

    public SessionResponse getSession(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> sessionMapper.toResponse(
                        session, (int) questionRepository.countBySessionIdAndAnsweredTrue(sessionId)))
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    public QuestionResponse continueSession(UUID sessionId, UUID userId) {
        checkUserSession(sessionId, userId);
        return questionRepository.findNextUnanswered(sessionId)
                .map(questionMapper::toDto)
                .orElseThrow(() -> {
                    log.warn("Cannot continue session {}, because it has no unanswered questions left", sessionId);
                    return new NotFoundException("This session finished");
                });
    }

    public QuestionResponse getQuestion(QuestionRequest request) {
        checkUserSession(request.sessionId(), request.userId());
        return questionRepository.findBySessionIdAndOrderIndex(request.sessionId(), request.index())
                .map(questionMapper::toDto)
                .orElseThrow(() -> {
                            log.warn("Question not found. Session: {}, Index: {}",
                                    request.sessionId(), request.index());
                            return new NotFoundException("Question not found");
                        }
                );
    }

    @Transactional
    public QuestionResponse submitAnswer(SubmitAnswerRequest request) {
        InterviewQuestion question = questionRepository.findWithSessionById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Question not found"));

        checkQuestionOwnership(question, request.userId());
        checkQuestionSession(question, request.sessionId());
        checkQuestionNotAnswered(question);

        question.setAnswerText(request.answerText());
        question.setAnsweredAt(Instant.now());
        question.setAnswered(true);

        if (request.evaluate()) {
            saveEvaluation(question, request.answerText());
        }

        if (question.getSession().getStatus().equals(SessionStatus.CREATED)) {
            question.getSession().setStatus(SessionStatus.IN_PROGRESS);
        }

        return questionMapper.toDto(question);
    }

    @Transactional
    public SessionReport finishSession(UUID sessionId, UUID userId) {
        InterviewSession session = sessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        checkSessionOwnership(session, userId);

        LlmReport llmReport = createLlmReport(session);

        Map<UUID, LlmAnswerReview> answersMap = llmReport.answers().stream()
                .collect(Collectors.toMap(LlmAnswerReview::id, Function.identity()));

        saveFeedbacks(session, answersMap);
        attachReport(session, llmReport, calculateAvgScore(answersMap));
        markCompleted(session);

        sessionRepository.save(session);

        return sessionMapper.toSessionReport(session);
    }

    public SessionReport getSessionReport(UUID sessionId, UUID userId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        return sessionMapper.toSessionReport(session);
    }

    public void deleteSession(UUID sessionId, UUID userId) {
        checkUserSession(sessionId, userId);
        sessionRepository.deleteById(sessionId);
    }

    private void checkSessionOwnership(InterviewSession session, UUID userId) {
        if (!session.getUserId().equals(userId)) {
            log.warn("Cannot finish session {}, because {} is not the owner", session.getId(), userId);
            throw new ForbiddenException("Access denied");
        }
    }

    private void attachReport(InterviewSession session, LlmReport llmReport, double avgScore) {
        session.setInterviewReport(InterviewReport.builder()
                .session(session)
                .avgScore(Math.round(avgScore * 10) / 10.0)
                .offerProbability(OfferProbability.valueOf(llmReport.offerProbability()))
                .overallFeedback(llmReport.overallFeedback())
                .build());
    }

    private void markCompleted(InterviewSession session) {
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
    }

    private void checkUserSession(UUID sessionId, UUID userId) {
        if (!sessionRepository.existsByIdAndUserId(sessionId, userId)) {
            log.error("User {} has no session with Id {}", userId, sessionId);
            throw new NotFoundException("Session not found");
        }
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

    private void checkQuestionNotAnswered(InterviewQuestion question) {
        if (question.isAnswered()) {
            throw new ConflictException("Question already answered");
        }
    }

    private void saveEvaluation(InterviewQuestion question, String answerText) {
        LlmAnswerEvaluation evaluation = llmService.evaluateAnswer(
                new LlmAnswerEvaluationRequest(
                        question.getSession().getProfession().name(),
                        question.getQuestionText(),
                        question.getSession().getLevel().name(),
                        answerText));

        AnswerFeedback feedback = feedbackRepository.save(
                AnswerFeedback.builder()
                        .question(question)
                        .score(evaluation.score())
                        .feedbackText(evaluation.feedback())
                        .build()
        );

        question.setFeedback(feedback);
    }


    private List<InterviewQuestion> createQuestions(CreateSessionRequest request, InterviewSession session) {
        List<BankQuestion> questions = questionBank.forLevel(request.level(), request.totalQuestions());

        if (questions.isEmpty()) {
            log.error("Question storage did not return any questions. {}", request);
            throw new InternalServerException("No questions found");
        }

        return IntStream.range(0, questions.size())
                .mapToObj(i -> {
                    var question = questionMapper.toEntity(questions.get(i), session);
                    question.setOrderIndex(i + 1);
                    return question;
                })
                .toList();
    }

    public LlmReport createLlmReport(InterviewSession session) {
        return llmService.createReport(
                new LlmReportRequest(
                        session.getProfession().name(),
                        session.getLevel().name(),
                        session.getQuestions().stream()
                                .map(q -> new LlmAnswer(
                                                q.getId(),
                                                q.getQuestionText(),
                                                q.getAnswerText(),
                                                q.getFeedback() != null ? q.getFeedback().getFeedbackText() : null,
                                                q.getFeedback() != null ? q.getFeedback().getScore() : null
                                        )
                                )
                                .toList()
                )
        );
    }

    private void saveFeedbacks(InterviewSession session, Map<UUID, LlmAnswerReview> answersMap) {
        session.getQuestions().stream()
                .filter(q -> q.getFeedback() == null)
                .forEach(q -> {
                    LlmAnswerReview review = answersMap.get(q.getId());
                    q.setFeedback(AnswerFeedback.builder()
                            .question(q)
                            .score(review.score())
                            .feedbackText(review.evaluation())
                            .build());
                });
    }

    private double calculateAvgScore(Map<UUID, LlmAnswerReview> answersMap) {
        return answersMap.values().stream()
                .map(LlmAnswerReview::score)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }
}
