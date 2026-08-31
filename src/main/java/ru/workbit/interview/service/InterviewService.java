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
import ru.workbit.llm.dto.LlmInterviewFollowUpDecision;
import ru.workbit.llm.dto.LlmInterviewFollowUpRequest;
import ru.workbit.llm.dto.LlmInterviewQuestions;
import ru.workbit.llm.dto.LlmInterviewQuestionsRequest;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewReportRequest;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

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

        List<String> questions = generateQuestions(vacancyData);

        InterviewSession session = interviewWriter.createSession(vacancyData, userId, questions);
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
        InterviewSession session = interviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        Optional<InterviewQuestion> pendingFollowUp = interviewQuestionRepository
                .findNextUnansweredFollowUp(sessionId);
        if (pendingFollowUp.isPresent()) {
            return interviewQuestionMapper.toDto(pendingFollowUp.get());
        }

        return askFollowUp(session)
                .or(() -> interviewQuestionRepository.findNextUnansweredMain(sessionId)
                        .map(interviewQuestionMapper::toDto))
                .orElseThrow(() -> {
                    log.warn("Interview session {} has no unanswered questions left", sessionId);
                    return new ConflictException("No questions left");
                });
    }

    private Optional<InterviewQuestionResponse> askFollowUp(InterviewSession session) {
        Optional<InterviewQuestion> lastAnswered = interviewQuestionRepository
                .findLastAnsweredWithoutFollowUpCheck(session.getId());
        if (lastAnswered.isEmpty()) {
            return Optional.empty();
        }

        InterviewQuestion answered = lastAnswered.get();
        boolean caseAlreadyClarified = answered.isFollowUp() || !interviewQuestionRepository
                .findAllByParentQuestionIdOrderByOrderIndex(answered.getId()).isEmpty();

        if (caseAlreadyClarified) {
            interviewWriter.markFollowUpChecked(answered.getId());
            return Optional.empty();
        }

        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        LlmInterviewFollowUpDecision decision = llmService.decideInterviewFollowUp(
                vacancy.experience(), new LlmInterviewFollowUpRequest(
                vacancy.name(),
                answered.getText(),
                answered.getAnswerText(),
                List.of()));

        if (!decision.askFollowUp() || decision.question() == null || decision.question().isBlank()) {
            interviewWriter.markFollowUpChecked(answered.getId());
            return Optional.empty();
        }

        try {
            return interviewWriter.saveFollowUp(answered.getId(), decision.question());
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request already created a follow-up for interview session {}", session.getId());
            return interviewQuestionRepository.findNextUnansweredFollowUp(session.getId())
                    .map(interviewQuestionMapper::toDto);
        }
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

        return interviewReportMapper.toResponse(report, session, answeredSorted(session));
    }

    private static LlmInterviewQuestionsRequest toQuestionsRequest(VacancyData vacancyData) {
        return new LlmInterviewQuestionsRequest(
                vacancyData.name(),
                vacancyData.employer(),
                vacancyData.keySkills(),
                vacancyData.description(),
                LlmInterviewQuestionsRequest.MIN_COUNT,
                LlmInterviewQuestionsRequest.MAX_COUNT
        );
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

    private List<String> generateQuestions(VacancyData vacancyData) {
        LlmInterviewQuestionsRequest request = toQuestionsRequest(vacancyData);
        List<String> questions = usableQuestions(
                llmService.generateInterviewQuestions(vacancyData.experience(), request));
        if (questions.size() < LlmInterviewQuestionsRequest.MIN_COUNT) {
            log.warn("LLM returned only {} usable interview questions, {} required, retrying [url={}]",
                    questions.size(), LlmInterviewQuestionsRequest.MIN_COUNT, vacancyData.url());
            questions = usableQuestions(
                    llmService.generateInterviewQuestions(vacancyData.experience(), request));
        }
        if (questions.size() < LlmInterviewQuestionsRequest.MIN_COUNT) {
            log.error("LLM returned only {} usable interview questions after retry, {} required [url={}]",
                    questions.size(), LlmInterviewQuestionsRequest.MIN_COUNT, vacancyData.url());
            throw new LlmException("Not enough questions for an interview session");
        }
        return questions;
    }

    private static List<String> usableQuestions(LlmInterviewQuestions llmQuestions) {
        return llmQuestions.questions() == null ? List.of() : llmQuestions.questions().stream()
                .filter(q -> q != null && !q.isBlank())
                .limit(LlmInterviewQuestionsRequest.MAX_COUNT)
                .toList();
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
