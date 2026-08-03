package ru.workbit.training.service;

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
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.training.dto.*;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.SkillDict;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.QuestionBankRepository;
import ru.workbit.content.repository.SkillDictRepository;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.mapper.TrainingQuestionMapper;
import ru.workbit.training.model.mapper.TrainingReportMapper;
import ru.workbit.training.model.mapper.TrainingSessionMapper;
import ru.workbit.training.repository.TrainingQuestionRepository;
import ru.workbit.training.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmTrainingCase;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.llm.service.LlmService;
import ru.workbit.util.DictText;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.workbit.training.service.TrainingSessions.answeredSorted;
import static ru.workbit.training.service.TrainingSessions.checkSessionNotCompleted;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingService {

    public static final int QUESTION_CAP = 10;
    public static final int MIN_ANSWERED_TO_FINISH = 3;
    public static final int SUGGEST_LIMIT = 7;
    public static final int OPTIONS_LIMIT = 20;
    public static final int MIN_SUGGEST_QUERY_LENGTH = 2;
    public static final int MAX_INPUT_LENGTH = 100;

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingQuestionRepository trainingQuestionRepository;
    private final ProfessionDictRepository professionDictRepository;
    private final SkillDictRepository skillDictRepository;
    private final QuestionBankRepository questionBankRepository;
    private final TrainingWriter trainingWriter;
    private final LlmService llmService;

    private final TrainingSessionMapper trainingSessionMapper;
    private final TrainingQuestionMapper trainingQuestionMapper;
    private final TrainingReportMapper trainingReportMapper;

    public TrainingSessionResponse create(CreateSessionRequest request, UUID userId) {
        TrainingSession session = trainingSessionMapper.toEntity(request);
        session.setUserId(userId);
        session.setSkill(DictText.normalize(session.getSkill()));
        session.setProfession(DictText.normalize(session.getProfession()));

        canonicalizeInput(session);

        TrainingWriter.DictionaryRefs refs = trainingWriter.upsertDictionaries(
                session.getSkill(), session.getProfession());
        List<BankQuestion> bankQuestions = questionBankRepository.sampleUnseen(
                refs.professionId(), refs.skillId(), session.getLevel().name(), userId, QUESTION_CAP);

        List<String> generatedQuestions = bankQuestions.size() < QUESTION_CAP
                ? generateMissingQuestions(session, bankQuestions)
                : List.of();
        checkEnoughQuestions(session, bankQuestions, generatedQuestions);

        return trainingWriter.createSession(session, bankQuestions, generatedQuestions);
    }

    public TrainingSessionResponse get(UUID sessionId, UUID userId) {
        return trainingSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> trainingSessionMapper.toResponse(session, (int) trainingQuestionRepository
                        .countByTrainingSessionIdAndAnsweredTrue(sessionId)))
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

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

    public List<TrainingSkillMatch> findLatestBySkills(UUID userId, Collection<String> skills) {
        Map<String, TrainingSession> latestBySkill = new HashMap<>();
        for (TrainingSession session : trainingSessionRepository.findAllByUserIdAndLoweredSkillIn(userId, skills)) {
            latestBySkill.putIfAbsent(session.getSkill().toLowerCase(), session);
        }
        return latestBySkill.values().stream()
                .map(session -> new TrainingSkillMatch(
                        session.getId(),
                        session.getSkill(),
                        session.getStatus(),
                        session.getReport() == null ? null : session.getReport().getAvgScore(),
                        (int) trainingQuestionRepository
                                .countByTrainingSessionIdAndAnsweredTrue(session.getId()),
                        (int) trainingQuestionRepository.countByTrainingSessionId(session.getId())))
                .toList();
    }

    public TrainingQuestionResponse nextQuestion(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        return trainingQuestionRepository.findNextUnanswered(sessionId)
                .map(trainingQuestionMapper::toDto)
                .orElseThrow(() -> {
                    log.warn("Session {} has no questions left to ask", sessionId);
                    return new ConflictException("Question cap reached");
                });
    }

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
        if (session.getStatus() == TrainingSession.Status.CREATED) {
            session.setStatus(TrainingSession.Status.IN_PROGRESS);
        }
    }

    /**
     * Эталонный ответ на вопрос: у вопроса из банка он скопирован при создании сессии, у сгенерированного
     * живьём — генерируется по первому запросу и кешируется, чтобы повторный показ не стоил вызова LLM.
     */
    public ReferenceAnswerResponse getReferenceAnswer(UUID sessionId, UUID questionId, UUID userId) {
        TrainingQuestion question = trainingQuestionRepository.findWithSessionById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        checkQuestionOwnership(question, userId);
        checkQuestionSession(question, sessionId);

        if (question.getReferenceAnswer() != null) {
            return new ReferenceAnswerResponse(question.getReferenceAnswer());
        }

        TrainingSession session = question.getTrainingSession();
        LlmTrainingReferenceAnswer generated = llmService.createReferenceAnswer(
                new LlmTrainingReferenceAnswerRequest(
                        session.getSkill(), session.getProfession(), question.getText()));
        if (generated.answer() == null || generated.answer().isBlank()) {
            log.error("LLM returned no usable reference answer for question {}", questionId);
            throw new LlmException("Reference answer is not available");
        }

        String answer = generated.answer().strip();
        trainingWriter.saveReferenceAnswer(questionId, answer);
        return new ReferenceAnswerResponse(answer);
    }

    public TrainingReportResponse createReport(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        List<TrainingQuestion> answered = answeredSorted(session);
        checkEnoughAnsweredToFinish(sessionId, answered);

        LlmTrainingReport llmReport = requestReport(sessionId, session, answered);

        try {
            return trainingWriter.completeReport(sessionId, llmReport);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already completed session {}", sessionId);
            throw new ConflictException("Session already finished");
        }
    }

    public TrainingReportResponse getReport(UUID sessionId, UUID userId) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));

        TrainingReport report = session.getReport();
        if (report == null) {
            throw new NotFoundException("Report not found");
        }

        return trainingReportMapper.toResponse(report, session, answeredSorted(session));
    }

    @Transactional
    public void delete(UUID sessionId, UUID userId) {
        checkUserSession(sessionId, userId);
        trainingSessionRepository.deleteById(sessionId);
    }

    public TrainingOptionsResponse getOptions() {
        return new TrainingOptionsResponse(
                skillDictRepository.findTopNames(OPTIONS_LIMIT),
                professionDictRepository.findTop20ByStatusOrderByUsageCountDesc(DictStatus.APPROVED).stream()
                        .map(ProfessionDict::getName)
                        .toList(),
                List.of(TrainingSession.Level.values()),
                QUESTION_CAP,
                MIN_ANSWERED_TO_FINISH);
    }

    public NormalizeInputResponse normalizeInput(NormalizeInputRequest request) {
        LlmInputNormalization normalized = llmService.normalizeInput(new LlmInputNormalizationRequest(
                DictText.normalize(request.skill()),
                DictText.normalize(request.profession())));

        return new NormalizeInputResponse(
                normalized.skillRecognized(),
                cleanSuggestions(normalized.skillSuggestions()),
                normalized.professionRecognized(),
                cleanSuggestions(normalized.professionSuggestions()),
                normalized.skillFitsProfession());
    }

    public List<String> suggestProfessions(String query) {
        if (isTooShortQuery(query)) {
            return List.of();
        }
        return professionDictRepository.suggest(escapeLike(DictText.normalize(query)), SUGGEST_LIMIT).stream()
                .map(ProfessionDict::getName)
                .toList();
    }

    /**
     * Профессия необязательна: навык — первое поле мастера, и до выбора профессии подсказки собираются
     * по всему словарю (одноимённые навыки разных профессий схлопываются).
     */
    public List<String> suggestSkills(String profession, String query) {
        if (isTooShortQuery(query)) {
            return List.of();
        }
        String escaped = escapeLike(DictText.normalize(query));
        if (profession == null || profession.isBlank()) {
            return skillDictRepository.suggestAcrossProfessions(escaped, SUGGEST_LIMIT);
        }
        return skillDictRepository.suggest(DictText.normalize(profession), escaped, SUGGEST_LIMIT).stream()
                .map(SkillDict::getName)
                .toList();
    }

    private static boolean isTooShortQuery(String query) {
        return query == null || query.strip().length() < MIN_SUGGEST_QUERY_LENGTH;
    }

    private static String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void canonicalizeInput(TrainingSession session) {
        Optional<ProfessionDict> profession = professionDictRepository.findByNameIgnoreCase(session.getProfession());
        boolean professionApproved = profession
                .filter(p -> p.getStatus() == DictStatus.APPROVED)
                .isPresent();
        boolean skillApproved = professionApproved && skillDictRepository
                .existsByProfessionIdAndNameIgnoreCaseAndStatus(
                        profession.get().getId(), session.getSkill(), DictStatus.APPROVED);
        if (professionApproved && skillApproved) {
            return;
        }

        LlmInputNormalization normalized = llmService.normalizeInput(new LlmInputNormalizationRequest(
                session.getSkill(),
                session.getProfession()));
        if (!skillApproved && !normalized.skillRecognized()) {
            log.warn("Rejecting training session: skill not recognized [skill={}, profession={}]",
                    session.getSkill(), session.getProfession());
            throw new UnprocessableEntityException("Skill not recognized");
        }
        if (!professionApproved && !normalized.professionRecognized()) {
            log.warn("Rejecting training session: profession not recognized [profession={}]",
                    session.getProfession());
            throw new UnprocessableEntityException("Profession not recognized");
        }

        if (!skillApproved) {
            session.setSkill(canonical(session.getSkill(), normalized.skillSuggestions()));
        }
        if (!professionApproved) {
            session.setProfession(canonical(session.getProfession(), normalized.professionSuggestions()));
        }
    }

    private static List<String> cleanSuggestions(List<String> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(DictText::normalize)
                .toList();
    }

    private static String canonical(String input, List<String> suggestions) {
        String canonical = suggestions == null ? null : suggestions.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(DictText::normalize)
                .filter(s -> s.length() <= MAX_INPUT_LENGTH)
                .findFirst()
                .orElse(null);

        if (canonical == null) {
            log.warn("LLM recognized input but returned no usable canonical name, keeping input as is [input={}]",
                    input);
            return input;
        }
        return canonical;
    }

    private List<String> generateMissingQuestions(TrainingSession session, List<BankQuestion> bankQuestions) {
        int missing = QUESTION_CAP - bankQuestions.size();
        LlmTrainingQuestions generated = llmService.generateTrainingQuestions(
                session.getLevel().getGrade(),
                new LlmTrainingQuestionsRequest(
                        session.getSkill(),
                        session.getProfession(),
                        missing,
                        bankQuestions.stream().map(BankQuestion::getText).toList()));

        List<String> questions = generated.questions() == null ? List.of() : generated.questions().stream()
                .filter(q -> q != null && !q.isBlank())
                .limit(missing)
                .toList();
        if (questions.size() < missing) {
            log.warn("LLM returned {} usable questions of {} requested [skill={}, profession={}, level={}]",
                    questions.size(), missing, session.getSkill(), session.getProfession(), session.getLevel());
        }
        return questions;
    }

    private void checkEnoughQuestions(TrainingSession session, List<BankQuestion> bankQuestions,
                                      List<String> generatedQuestions) {
        int questions = bankQuestions.size() + generatedQuestions.size();
        if (questions < MIN_ANSWERED_TO_FINISH) {
            log.error("Only {} questions for new training session, {} required "
                            + "[skill={}, profession={}, level={}]",
                    questions, MIN_ANSWERED_TO_FINISH, session.getSkill(), session.getProfession(),
                    session.getLevel());
            throw new LlmException("Not enough questions for a training session");
        }
    }

    /**
     * Запрос отчёта с одним повторным вызовом на вырожденный ответ-заглушку: Studio изредка отдаёт
     * шаблон схемы вместо отчёта ("string" в полях, один case) — тот же класс сбоя, что у
     * интервью-ревьюера и генераторов вопросов. Итоговую валидацию делает completeReport.
     */
    private LlmTrainingReport requestReport(UUID sessionId, TrainingSession session,
                                            List<TrainingQuestion> answered) {
        LlmTrainingReportRequest request = new LlmTrainingReportRequest(
                session.getSkill(),
                session.getProfession(),
                IntStream.range(0, answered.size())
                        .mapToObj(i -> new LlmTrainingCase(
                                i + 1, answered.get(i).getText(), answered.get(i).getAnswerText()))
                        .toList());
        LlmTrainingReport report = llmService.createTrainingReport(request);
        if (isUsableReport(report, answered.size())) {
            return report;
        }
        log.warn("LLM returned degenerate training report for session {}, retrying once", sessionId);
        return llmService.createTrainingReport(request);
    }

    private static boolean isUsableReport(LlmTrainingReport report, int questionsCount) {
        return report.overallFeedback() != null
                && !report.overallFeedback().isBlank()
                && report.overallFeedback().length() >= TrainingWriter.MIN_OVERALL_FEEDBACK_LENGTH
                && report.cases() != null
                && report.cases().size() >= questionsCount * TrainingWriter.MIN_REVIEWED_QUESTIONS_RATIO;
    }

    private void checkEnoughAnsweredToFinish(UUID sessionId, List<TrainingQuestion> answered) {
        if (answered.size() < MIN_ANSWERED_TO_FINISH) {
            log.warn("Session {} has only {} answered questions, {} required to finish",
                    sessionId, answered.size(), MIN_ANSWERED_TO_FINISH);
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

    private void checkQuestionNotAnswered(TrainingQuestion question) {
        if (question.isAnswered()) {
            throw new ConflictException("Question already answered");
        }
    }
}
