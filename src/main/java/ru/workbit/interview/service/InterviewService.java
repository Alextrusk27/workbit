package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.FeedbackRequest;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.InterviewUserFeedback;
import ru.workbit.interview.model.mapper.InterviewQuestionMapper;
import ru.workbit.interview.model.mapper.InterviewReportMapper;
import ru.workbit.interview.model.mapper.InterviewSessionMapper;
import ru.workbit.interview.repository.InterviewQuestionRepository;
import ru.workbit.interview.repository.InterviewSessionRepository;
import ru.workbit.interview.repository.InterviewUserFeedbackRepository;
import ru.workbit.llm.dto.LlmInterviewAnswer;
import ru.workbit.llm.dto.LlmInterviewFollowUp;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewReportRequest;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static ru.workbit.interview.service.InterviewSessions.answeredMainSorted;
import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

    private static final int MAX_REDIRECTS = 3;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewUserFeedbackRepository interviewUserFeedbackRepository;
    private final InterviewWriter interviewWriter;
    private final VacancyService vacancyService;
    private final LlmService llmService;
    private final QuotaService quotaService;

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewReportMapper interviewReportMapper;

    public InterviewSessionResponse createSession(String vacancyUrl, UUID userId) {
        VacancyData vacancyData = vacancyService.fetch(vacancyUrl);

        checkNoUnfinishedInterview(vacancyData, userId);

        quotaService.checkInterviewAvailable(userId);

        LlmInterviewPlan plan = requestPlan(vacancyData);

        InterviewSession session = interviewWriter.createSession(vacancyData, userId, plan);
        return interviewSessionMapper.toResponse(session, vacancyData, 0);
    }

    public InterviewSessionResponse get(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        int answeredCount = (int) interviewQuestionRepository
                .countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId);
        return interviewSessionMapper.toResponse(session, vacancy, answeredCount);
    }

    public InterviewQuestionResponse nextQuestion(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        return unanswered(session)
                .map(interviewQuestionMapper::toDto)
                .or(() -> askNextStep(session))
                .orElseThrow(() -> new ConflictException("No questions left"));
    }

    public List<InterviewQuestionResponse> getAnsweredQuestions(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));

        return groupCases(answeredSorted(session)).stream()
                .flatMap(List::stream)
                .map(interviewQuestionMapper::toDto)
                .toList();
    }

    @Transactional
    public void submitAnswer(SubmitAnswerRequest request) {
        InterviewQuestion question = interviewQuestionRepository.findWithSessionById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Question not found"));

        checkQuestionOwnership(question, request.userId());
        checkQuestionSession(question, request.sessionId());
        checkSessionNotCompleted(question.getSession());
        checkQuestionNotAnswered(question);

        question.setAnswerText(request.answerText());
        question.setAnsweredAt(Instant.now());
        question.setAnswered(true);

        InterviewSession session = question.getSession();
        if (session.getStatus() == InterviewSession.Status.CREATED) {
            session.setStatus(InterviewSession.Status.IN_PROGRESS);
        }
    }

    @Transactional
    public void submitQuestionFeedback(UUID sessionId, UUID questionId, UUID userId, FeedbackRequest request) {
        InterviewQuestion question = interviewQuestionRepository.findWithSessionById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        checkQuestionOwnership(question, userId);
        checkQuestionSession(question, sessionId);

        interviewUserFeedbackRepository.save(buildUserFeedback(sessionId, questionId, request));
    }

    @Transactional
    public void submitReportFeedback(UUID sessionId, UUID userId, FeedbackRequest request) {
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getReport() == null) {
            throw new NotFoundException("Report not found");
        }

        interviewUserFeedbackRepository.save(buildUserFeedback(sessionId, null, request));
    }

    public InterviewReportResponse createReport(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        List<InterviewQuestion> answered = answeredSorted(session);
        checkAllQuestionsAnswered(session, answered);

        List<List<InterviewQuestion>> cases = groupCases(answered);
        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        LlmInterviewReport llmReport = requestReport(sessionId, vacancy, cases);

        try {
            return interviewWriter.completeReport(sessionId, llmReport);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already completed interview session {}", sessionId);
            throw new ConflictException("Session already finished");
        }
    }

    public InterviewReportResponse getReport(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));

        InterviewReport report = session.getReport();
        if (report == null) {
            throw new NotFoundException("Report not found");
        }

        return interviewReportMapper.toResponse(report, session, answeredMainSorted(session));
    }

    private void checkNoUnfinishedInterview(VacancyData vacancyData, UUID userId) {
        List<UUID> snapshotIds = vacancyService.getSnapshotIds(vacancyData.sourceId());
        if (!snapshotIds.isEmpty() && interviewSessionRepository
                .existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                        userId, snapshotIds, InterviewSession.Status.COMPLETED)) {
            log.warn("User {} already has an unfinished interview for vacancy {}", userId, vacancyData.sourceId());
            throw new ConflictException("Unfinished interview exists");
        }
    }

    /**
     * План собеседования с одним повторным вызовом на ответ без первого вопроса. Число основных
     * вопросов выбирает модель, код обрезает его в допустимый коридор.
     */
    private LlmInterviewPlan requestPlan(VacancyData vacancyData) {
        LlmInterviewVacancy vacancy = new LlmInterviewVacancy(vacancyData.name(), vacancyData.employer(),
                vacancyData.experience(), vacancyData.keySkills(), vacancyData.description());

        LlmInterviewPlan plan = llmService.planInterview(vacancy);
        if (!isUsablePlan(plan)) {
            log.warn("LLM returned an interview plan without the first question, retrying [url={}]",
                    vacancyData.url());
            plan = llmService.planInterview(vacancy);
        }
        if (!isUsablePlan(plan)) {
            log.error("LLM returned an interview plan without the first question after retry [url={}]",
                    vacancyData.url());
            throw new LlmException("Interview plan has no first question");
        }

        return new LlmInterviewPlan(
                Math.clamp(plan.questionCount(), LlmInterviewPlan.MIN_COUNT, LlmInterviewPlan.MAX_COUNT),
                plan.topics(),
                plan.topic(),
                plan.question());
    }

    private static boolean isUsablePlan(LlmInterviewPlan plan) {
        return plan.question() != null && !plan.question().isBlank();
    }

    private static Optional<InterviewQuestion> unanswered(InterviewSession session) {
        return session.getQuestions().stream()
                .filter(q -> !q.isAnswered())
                .max(Comparator.<InterviewQuestion, Boolean>comparing(InterviewQuestion::isFollowUp)
                        .thenComparingInt(InterviewQuestion::getOrderIndex));
    }

    /**
     * Очередной ход беседы: модель получает вакансию, план и всю историю и возвращает следующую реплику.
     * Что с ней делать, решает код: не больше одного уточнения на основной вопрос (второе идёт как
     * новый основной), основной сверх плана и третий возврат к теме завершают интервью.
     */
    private Optional<InterviewQuestionResponse> askNextStep(InterviewSession session) {
        List<List<InterviewQuestion>> cases = groupCases(answeredSorted(session));
        if (cases.isEmpty()) {
            return Optional.empty();
        }

        List<InterviewQuestion> dialog = cases.stream().flatMap(List::stream).toList();
        InterviewQuestion answered = dialog.getLast();
        if (answered.isFollowUpChecked()) {
            return Optional.empty();
        }

        LlmInterviewStep step = requestStep(session, dialog, cases.size());
        InterviewQuestion.Kind kind = resolveKind(step.kind(), cases.getLast());

        if (isFinalStep(kind, session.getTotalQuestions(), cases.size(), redirects(dialog))) {
            interviewWriter.closeQuestioning(answered.getId());
            return Optional.empty();
        }

        try {
            return interviewWriter.saveStep(answered.getId(), kind, step.question(), step.topic());
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already asked the next question in interview session {}", session.getId());
            return interviewQuestionRepository.findNextUnanswered(session.getId())
                    .map(interviewQuestionMapper::toDto);
        }
    }

    /**
     * Запрос реплики с одним повторным вызовом на вырожденный ответ: пустой вопрос допустим только у
     * {@code MAIN}, когда основные исчерпаны, - это сигнал конца беседы.
     */
    private LlmInterviewStep requestStep(InterviewSession session, List<InterviewQuestion> dialog, int mainAsked) {
        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        LlmInterviewVacancy llmVacancy = new LlmInterviewVacancy(vacancy.name(), vacancy.employer(),
                vacancy.experience(), vacancy.keySkills(), vacancy.description());
        LlmInterviewPlan plan = new LlmInterviewPlan(session.getTotalQuestions(), session.getPlanTopics(),
                dialog.getFirst().getTopic(), dialog.getFirst().getText());
        List<LlmInterviewTurn> history = IntStream.range(0, dialog.size() - 1)
                .mapToObj(i -> new LlmInterviewTurn(dialog.get(i).getAnswerText(), toStep(dialog.get(i + 1))))
                .toList();
        String lastAnswer = dialog.getLast().getAnswerText();

        LlmInterviewStep step = llmService.nextInterviewStep(llmVacancy, plan, history, lastAnswer);
        if (isUsableStep(step, mainAsked, session.getTotalQuestions())) {
            return step;
        }

        log.warn("LLM returned degenerate interview step for session {}, retrying once [kind={}, blankQuestion={}, mainAsked={}/{}]",
                session.getId(), step.kind(), isBlank(step.question()), mainAsked, session.getTotalQuestions());
        step = llmService.nextInterviewStep(llmVacancy, plan, history, lastAnswer);
        if (isUsableStep(step, mainAsked, session.getTotalQuestions())) {
            return step;
        }

        log.error("LLM returned degenerate interview step for session {} after retry [kind={}, blankQuestion={}, mainAsked={}/{}]",
                session.getId(), step.kind(), isBlank(step.question()), mainAsked, session.getTotalQuestions());
        throw new LlmException("Interview step has no question");
    }

    private static LlmInterviewStep toStep(InterviewQuestion question) {
        return new LlmInterviewStep(
                LlmInterviewStepKind.valueOf(question.getKind().name()),
                question.getTopic(),
                question.getText());
    }

    private static boolean isUsableStep(LlmInterviewStep step, int mainAsked, int totalQuestions) {
        if (step.kind() == null) {
            return false;
        }
        return !isBlank(step.question())
                || step.kind() == LlmInterviewStepKind.MAIN && mainAsked >= totalQuestions;
    }

    private static boolean isBlank(String question) {
        return question == null || question.isBlank();
    }

    private static InterviewQuestion.Kind resolveKind(LlmInterviewStepKind kind, List<InterviewQuestion> currentCase) {
        return switch (kind) {
            case MAIN -> InterviewQuestion.Kind.MAIN;
            case FOLLOW_UP -> currentCase.stream().anyMatch(q -> q.getKind() == InterviewQuestion.Kind.FOLLOW_UP)
                    ? InterviewQuestion.Kind.MAIN
                    : InterviewQuestion.Kind.FOLLOW_UP;
            case CLARIFICATION -> InterviewQuestion.Kind.CLARIFICATION;
            case REDIRECT -> InterviewQuestion.Kind.REDIRECT;
        };
    }

    private static int redirects(List<InterviewQuestion> dialog) {
        return (int) dialog.stream().filter(q -> q.getKind() == InterviewQuestion.Kind.REDIRECT).count();
    }

    private static boolean isFinalStep(InterviewQuestion.Kind kind, int totalQuestions, int mainAsked, int redirects) {
        return kind == InterviewQuestion.Kind.MAIN && mainAsked >= totalQuestions
                || kind == InterviewQuestion.Kind.REDIRECT && redirects >= MAX_REDIRECTS - 1;
    }

    private void checkAllQuestionsAnswered(InterviewSession session, List<InterviewQuestion> answered) {
        long answeredMain = answered.stream().filter(q -> !q.isFollowUp()).count();
        if (answeredMain < session.getTotalQuestions()) {
            log.warn("Interview session {} has only {} of {} answered questions, cannot finish",
                    session.getId(), answeredMain, session.getTotalQuestions());
            throw new ConflictException("Not all questions answered");
        }
    }

    /**
     * Запрос отчёта с одним повторным вызовом на вырожденный ответ-заглушку: Studio изредка отдаёт
     * шаблон схемы вместо отчёта ("string" в полях, один answer) — тот же класс сбоя, что и у
     * генератора вопросов в {@link #generateQuestions}. Итоговую валидацию делает completeReport.
     */
    private LlmInterviewReport requestReport(UUID sessionId, VacancySnapshotView vacancy,
                                             List<List<InterviewQuestion>> cases) {
        LlmInterviewReportRequest request = new LlmInterviewReportRequest(
                vacancy.name(),
                vacancy.experience(),
                IntStream.range(0, cases.size())
                        .mapToObj(i -> toLlmAnswer(i + 1, cases.get(i)))
                        .toList());
        LlmInterviewReport report = llmService.createInterviewReport(vacancy.experience(), request);
        if (isUsableReport(report, cases.size())) {
            return report;
        }
        log.warn("LLM returned degenerate interview report for session {}, retrying once", sessionId);
        return llmService.createInterviewReport(vacancy.experience(), request);
    }

    private static boolean isUsableReport(LlmInterviewReport report, int casesCount) {
        return report.overallFeedback() != null
                && !report.overallFeedback().isBlank()
                && report.overallFeedback().length() >= InterviewWriter.MIN_OVERALL_FEEDBACK_LENGTH
                && InterviewReport.OfferProbability.fromString(report.offerProbability()).isPresent()
                && report.answers() != null
                && report.answers().size() >= casesCount * InterviewWriter.MIN_REVIEWED_ANSWERS_RATIO;
    }

    private static LlmInterviewAnswer toLlmAnswer(int index, List<InterviewQuestion> interviewCase) {
        return new LlmInterviewAnswer(
                index,
                interviewCase.getFirst().getText(),
                interviewCase.getFirst().getAnswerText(),
                interviewCase.stream()
                        .skip(1)
                        .map(q -> new LlmInterviewFollowUp(q.getText(), q.getAnswerText()))
                        .toList());
    }

    private static InterviewUserFeedback buildUserFeedback(UUID sessionId, UUID questionId,
                                                           FeedbackRequest request) {
        return InterviewUserFeedback.builder()
                .sessionId(sessionId)
                .questionId(questionId)
                .vote(request.vote())
                .reasons(request.reasons())
                .comment(request.comment())
                .build();
    }

    private void checkQuestionOwnership(InterviewQuestion question, UUID userId) {
        if (!question.getSession().getUserId().equals(userId)) {
            log.warn("IDOR attempt: user {} tried to access interview question {} owned by user {}",
                    userId, question.getId(), question.getSession().getUserId());
            throw new ForbiddenException("Access denied");
        }
    }

    private void checkQuestionSession(InterviewQuestion question, UUID sessionId) {
        if (!question.getSession().getId().equals(sessionId)) {
            log.warn("Interview question {} belongs to session {}, but request came with session {}",
                    question.getId(), question.getSession().getId(), sessionId);
            throw new ConflictException("Invalid session");
        }
    }

    private void checkQuestionNotAnswered(InterviewQuestion question) {
        if (question.isAnswered()) {
            throw new ConflictException("Question already answered");
        }
    }
}
