package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.*;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.TopicDict;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.QuestionBankRepository;
import ru.workbit.content.repository.TopicDictRepository;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingReport;
import ru.workbit.interview.model.TrainingSession;
import ru.workbit.interview.model.mapper.TrainingQuestionMapper;
import ru.workbit.interview.model.mapper.TrainingReportMapper;
import ru.workbit.interview.model.mapper.TrainingSessionMapper;
import ru.workbit.interview.repository.TrainingQuestionRepository;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmTrainingCase;
import ru.workbit.llm.dto.LlmTrainingFollowUp;
import ru.workbit.llm.dto.LlmTrainingHistoryItem;
import ru.workbit.llm.dto.LlmTrainingQuestion;
import ru.workbit.llm.dto.LlmTrainingQuestionRequest;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.llm.service.LlmService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingService extends BaseInterviewService<TrainingSessionResponse, TrainingQuestionResponse,
        TrainingReportResponse> {

    public static final int MAIN_QUESTION_CAP = 10;
    public static final int MIN_ANSWERED_TO_FINISH = 3;
    public static final int MAX_FOLLOW_UPS_PER_QUESTION = 4;
    public static final int SUGGEST_LIMIT = 7;
    public static final int MIN_SUGGEST_QUERY_LENGTH = 2;

    private static final String FOLLOW_UP_TYPE = "FOLLOW_UP";

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingQuestionRepository trainingQuestionRepository;
    private final ProfessionDictRepository professionDictRepository;
    private final TopicDictRepository topicDictRepository;
    private final QuestionBankRepository questionBankRepository;
    private final TrainingWriter trainingWriter;
    private final LlmService llmService;

    private final TrainingSessionMapper trainingSessionMapper;
    private final TrainingQuestionMapper trainingQuestionMapper;
    private final TrainingReportMapper trainingReportMapper;

    @Override
    public TrainingSessionResponse create(CreateSessionRequest request, UUID userId) {
        TrainingSession session = trainingSessionMapper.toEntity(request);
        session.setUserId(userId);
        if (session.getTopic() != null && session.getTopic().isBlank()) {
            session.setTopic(null);
        }

        TrainingWriter.DictionaryRefs refs = trainingWriter.upsertDictionaries(
                session.getProfession(), session.getTopic());
        List<BankQuestion> bankQuestions = questionBankRepository.sampleUnseen(
                refs.professionId(), refs.topicId(), session.getLevel().name(), userId, MAIN_QUESTION_CAP);

        List<String> generatedQuestions = bankQuestions.size() < MAIN_QUESTION_CAP
                ? generateMissingQuestions(session, bankQuestions)
                : List.of();
        checkHasMainQuestions(session, bankQuestions, generatedQuestions);

        return trainingWriter.createSession(session, bankQuestions, generatedQuestions);
    }

    @Override
    public TrainingSessionResponse get(UUID sessionId, UUID userId) {
        return trainingSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> trainingSessionMapper.toResponse(session, (int) trainingQuestionRepository
                        .countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)))
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    @Override
    public Page<@NotNull TrainingSessionResponse> getAll(UUID userId, Pageable pageable) {
        Page<@NotNull TrainingSession> sessions = trainingSessionRepository.findAllByUserId(userId, pageable);

        Map<UUID, Long> answered = trainingQuestionRepository
                .countAnsweredBySessionIds(sessions.stream().map(TrainingSession::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        TrainingQuestionRepository.AnsweredCount::getSessionId,
                        TrainingQuestionRepository.AnsweredCount::getCount));

        return sessions.map(session -> trainingSessionMapper.toResponse(
                session,
                answered.getOrDefault(session.getId(), 0L).intValue()));
    }

    @Override
    public TrainingQuestionResponse nextQuestion(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        Optional<TrainingQuestion> unanswered = trainingQuestionRepository.findNextUnanswered(sessionId);
        if (unanswered.isPresent()) {
            return trainingQuestionMapper.toDto(unanswered.get());
        }

        long answeredMain = trainingQuestionRepository
                .countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(sessionId);
        checkCapNotReached(sessionId, answeredMain);

        List<TrainingQuestion> history = trainingQuestionRepository
                .findAllByTrainingSessionIdOrderByOrderIndex(sessionId);
        boolean allowFollowUp = !history.isEmpty()
                && trailingFollowUps(history) < MAX_FOLLOW_UPS_PER_QUESTION;

        LlmTrainingQuestion generated = llmService.generateTrainingQuestion(new LlmTrainingQuestionRequest(
                session.getProfession(),
                session.getLevel().getName(),
                history.stream()
                        .map(q -> new LlmTrainingHistoryItem(q.getQuestionText(), q.getAnswerText(), q.isFollowUp()))
                        .toList(),
                (int) answeredMain + 1,
                allowFollowUp));

        checkGeneratedQuestion(sessionId, generated);
        boolean followUp = allowFollowUp && FOLLOW_UP_TYPE.equals(generated.type());

        try {
            return trainingWriter.saveQuestion(sessionId, generated.question(), followUp);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already created a question for session {}", sessionId);
            return trainingQuestionRepository.findNextUnanswered(sessionId)
                    .map(trainingQuestionMapper::toDto)
                    .orElseThrow(() -> new ConflictException("Concurrent session update"));
        }
    }

    @Override
    @Transactional
    public void submitAnswer(SubmitAnswerRequest request) {
        TrainingQuestion question = trainingQuestionRepository.findWithSessionById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Question not found"));

        checkQuestionOwnership(question, request.userId());
        checkQuestionSession(question, request.sessionId());
        checkSessionNotCompleted(question.getTrainingSession());
        checkQuestionNotAnswered(question);

        question.setAnswerText(request.answerText());
        question.setAnsweredAt(Instant.now());
        question.setAnswered(true);

        TrainingSession session = question.getTrainingSession();
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
        }
    }

    @Override
    public TrainingReportResponse createReport(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        List<TrainingQuestion> answered = session.getQuestions().stream()
                .filter(TrainingQuestion::isAnswered)
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .toList();
        checkEnoughAnsweredToFinish(sessionId, answered);

        List<List<TrainingQuestion>> cases = TrainingWriter.groupCases(answered);
        LlmTrainingReport llmReport = llmService.createTrainingReport(new LlmTrainingReportRequest(
                session.getProfession(),
                session.getLevel().getName(),
                IntStream.range(0, cases.size())
                        .mapToObj(i -> toLlmCase(i + 1, cases.get(i)))
                        .toList()));

        try {
            return trainingWriter.completeReport(sessionId, llmReport);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already completed session {}", sessionId);
            throw new ConflictException("Session already finished");
        }
    }

    @Override
    public TrainingReportResponse getReport(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));

        TrainingReport report = session.getReport();
        if (report == null) {
            throw new NotFoundException("Report not found");
        }

        List<TrainingQuestion> answered = session.getQuestions().stream()
                .filter(TrainingQuestion::isAnswered)
                .sorted(Comparator.comparingInt(TrainingQuestion::getOrderIndex))
                .toList();

        return trainingReportMapper.toResponse(report, session, answered);
    }

    @Override
    @Transactional
    public void delete(UUID sessionId, UUID userId) {
        checkUserSession(sessionId, userId);
        trainingSessionRepository.deleteById(sessionId);
    }

    public TrainingOptionsResponse getOptions() {
        return new TrainingOptionsResponse(
                professionDictRepository.findTop20ByOrderByUsageCountDesc().stream()
                        .map(ProfessionDict::getName)
                        .toList(),
                List.of(Level.values()),
                MAIN_QUESTION_CAP,
                MIN_ANSWERED_TO_FINISH);
    }

    public NormalizeInputResponse normalizeInput(NormalizeInputRequest request) {
        boolean hasTopic = request.topic() != null && !request.topic().isBlank();
        LlmInputNormalization normalized = llmService.normalizeInput(new LlmInputNormalizationRequest(
                request.profession().strip(),
                hasTopic ? request.topic().strip() : ""));

        return new NormalizeInputResponse(
                normalized.professionRecognized(),
                normalized.professionSuggestions() != null ? normalized.professionSuggestions() : List.of(),
                hasTopic ? normalized.topicRecognized() : null,
                hasTopic ? (normalized.topicSuggestions() != null ? normalized.topicSuggestions() : List.of()) : null,
                hasTopic ? normalized.topicFitsProfession() : null);
    }

    public List<String> suggestProfessions(String query) {
        if (isTooShortQuery(query)) {
            return List.of();
        }
        return professionDictRepository.suggest(escapeLike(query.strip()), SUGGEST_LIMIT).stream()
                .map(ProfessionDict::getName)
                .toList();
    }

    public List<String> suggestTopics(String profession, String query) {
        if (profession == null || profession.isBlank() || isTooShortQuery(query)) {
            return List.of();
        }
        return topicDictRepository.suggest(profession.strip(), escapeLike(query.strip()), SUGGEST_LIMIT).stream()
                .map(TopicDict::getName)
                .toList();
    }

    private static boolean isTooShortQuery(String query) {
        return query == null || query.strip().length() < MIN_SUGGEST_QUERY_LENGTH;
    }

    private static String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<String> generateMissingQuestions(TrainingSession session, List<BankQuestion> bankQuestions) {
        int missing = MAIN_QUESTION_CAP - bankQuestions.size();
        LlmTrainingQuestions generated = llmService.generateTrainingQuestions(new LlmTrainingQuestionsRequest(
                session.getProfession(),
                session.getTopic() != null ? session.getTopic() : "",
                session.getLevel().getName(),
                missing,
                bankQuestions.stream().map(BankQuestion::getText).toList()));

        List<String> questions = generated.questions() == null ? List.of() : generated.questions().stream()
                .filter(q -> q != null && !q.isBlank())
                .limit(missing)
                .toList();
        if (questions.size() < missing) {
            log.warn("LLM returned {} usable questions of {} requested [profession={}, topic={}, level={}]",
                    questions.size(), missing, session.getProfession(), session.getTopic(), session.getLevel());
        }
        return questions;
    }

    private void checkHasMainQuestions(TrainingSession session, List<BankQuestion> bankQuestions,
                                       List<String> generatedQuestions) {
        if (bankQuestions.isEmpty() && generatedQuestions.isEmpty()) {
            log.error("No main questions for new training session [profession={}, topic={}, level={}]",
                    session.getProfession(), session.getTopic(), session.getLevel());
            throw new LlmException("Generated questions are empty");
        }
    }

    private static int trailingFollowUps(List<TrainingQuestion> history) {
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && history.get(i).isFollowUp(); i--) {
            count++;
        }
        return count;
    }

    private static LlmTrainingCase toLlmCase(int index, List<TrainingQuestion> trainingCase) {
        return new LlmTrainingCase(
                index,
                trainingCase.getFirst().getQuestionText(),
                trainingCase.getFirst().getAnswerText(),
                trainingCase.stream()
                        .skip(1)
                        .map(q -> new LlmTrainingFollowUp(q.getQuestionText(), q.getAnswerText()))
                        .toList());
    }

    private void checkCapNotReached(UUID sessionId, long answeredMain) {
        if (answeredMain >= MAIN_QUESTION_CAP) {
            log.warn("Session {} reached the main question cap of {}", sessionId, MAIN_QUESTION_CAP);
            throw new ConflictException("Question cap reached");
        }
    }

    private void checkGeneratedQuestion(UUID sessionId, LlmTrainingQuestion generated) {
        if (generated.question() == null || generated.question().isBlank()) {
            log.error("LLM returned a blank training question for session {}", sessionId);
            throw new LlmException("Generated question is empty");
        }
    }

    private void checkEnoughAnsweredToFinish(UUID sessionId, List<TrainingQuestion> answered) {
        long answeredMain = answered.stream().filter(q -> !q.isFollowUp()).count();
        if (answeredMain < MIN_ANSWERED_TO_FINISH) {
            log.warn("Session {} has only {} answered main questions, {} required to finish",
                    sessionId, answeredMain, MIN_ANSWERED_TO_FINISH);
            throw new ConflictException("Not enough answered questions to finish");
        }
    }

    private void checkUserSession(UUID sessionId, UUID userId) {
        if (!trainingSessionRepository.existsByIdAndUserId(sessionId, userId)) {
            log.warn("User {} has no session with Id {}", userId, sessionId);
            throw new NotFoundException("Session not found");
        }
    }

    private void checkQuestionOwnership(TrainingQuestion question, UUID userId) {
        if (!question.getTrainingSession().getUserId().equals(userId)) {
            log.warn("IDOR attempt: user {} tried to access question {} owned by user {}",
                    userId, question.getId(), question.getTrainingSession().getUserId());
            throw new ForbiddenException("Access denied");
        }
    }

    private void checkQuestionSession(TrainingQuestion question, UUID sessionId) {
        if (!question.getTrainingSession().getId().equals(sessionId)) {
            log.warn("Question {} belongs to session {}, but request came with session {}",
                    question.getId(), question.getTrainingSession().getId(), sessionId);
            throw new ConflictException("Invalid session");
        }
    }

    private void checkSessionNotCompleted(TrainingSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            log.warn("Session {} is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }

    private void checkQuestionNotAnswered(TrainingQuestion question) {
        if (question.isAnswered()) {
            throw new ConflictException("Question already answered");
        }
    }
}
