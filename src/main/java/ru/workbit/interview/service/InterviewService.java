package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.mapper.InterviewQuestionMapper;
import ru.workbit.interview.model.mapper.InterviewReportMapper;
import ru.workbit.interview.model.mapper.InterviewSessionMapper;
import ru.workbit.interview.repository.InterviewQuestionRepository;
import ru.workbit.interview.repository.InterviewSessionRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

    public static final int MAX_FOLLOW_UPS_PER_QUESTION = 2;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewWriter interviewWriter;
    private final VacancyService vacancyService;
    private final LlmService llmService;

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewReportMapper interviewReportMapper;

    public InterviewSessionResponse createSession(String vacancyUrl, UUID userId) {
        VacancyData vacancyData = vacancyService.fetch(vacancyUrl);

        LlmInterviewQuestions llmQuestions = llmService.generateInterviewQuestions(
                vacancyData.experience(), toQuestionsRequest(vacancyData));
        List<String> questions = usableQuestions(llmQuestions, vacancyData);

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

    public List<InterviewSessionResponse> getAll(UUID userId) {
        List<InterviewSession> sessions = interviewSessionRepository.findAllByUserIdOrderByCreatedDesc(userId);

        Map<UUID, Long> answered = interviewQuestionRepository
                .countAnsweredBySessionIds(sessions.stream().map(InterviewSession::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        InterviewQuestionRepository.AnsweredCount::getSessionId,
                        InterviewQuestionRepository.AnsweredCount::getCount));

        Map<UUID, VacancySnapshotView> vacancies = vacancyService.getSnapshotViews(
                sessions.stream().map(InterviewSession::getVacancySnapshotId).toList());

        return sessions.stream()
                .map(session -> interviewSessionMapper.toResponse(
                        session,
                        vacancies.get(session.getVacancySnapshotId()),
                        answered.getOrDefault(session.getId(), 0L).intValue()))
                .toList();
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
        UUID caseMainId = answered.isFollowUp() ? answered.getParentQuestionId() : answered.getId();
        List<InterviewQuestion> caseFollowUps = interviewQuestionRepository
                .findAllByParentQuestionIdOrderByOrderIndex(caseMainId);

        if (caseFollowUps.size() >= MAX_FOLLOW_UPS_PER_QUESTION) {
            interviewWriter.markFollowUpChecked(answered.getId());
            return Optional.empty();
        }

        InterviewQuestion caseMain = answered.isFollowUp()
                ? interviewQuestionRepository.findById(caseMainId)
                        .orElseThrow(() -> new NotFoundException("Question not found"))
                : answered;

        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        LlmInterviewFollowUpDecision decision = llmService.decideInterviewFollowUp(
                vacancy.experience(), new LlmInterviewFollowUpRequest(
                vacancy.name(),
                caseMain.getText(),
                caseMain.getAnswerText(),
                caseFollowUps.stream()
                        .map(q -> new LlmInterviewFollowUp(q.getText(), q.getAnswerText()))
                        .toList()));

        if (!decision.askFollowUp() || decision.question() == null || decision.question().isBlank()) {
            interviewWriter.markFollowUpChecked(answered.getId());
            return Optional.empty();
        }

        try {
            return Optional.of(interviewWriter.saveFollowUp(answered.getId(), caseMainId, decision.question()));
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

    public InterviewReportResponse createReport(UUID sessionId, UUID userId) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        List<InterviewQuestion> answered = answeredSorted(session);
        checkAllQuestionsAnswered(session, answered);

        List<List<InterviewQuestion>> cases = groupCases(answered);
        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
        LlmInterviewReport llmReport = llmService.createInterviewReport(vacancy.experience(), new LlmInterviewReportRequest(
                vacancy.name(),
                vacancy.experience(),
                IntStream.range(0, cases.size())
                        .mapToObj(i -> toLlmAnswer(i + 1, cases.get(i)))
                        .toList()));

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

    @Transactional
    public void delete(UUID sessionId, UUID userId) {
        if (!interviewSessionRepository.existsByIdAndUserId(sessionId, userId)) {
            log.warn("User {} has no interview session {}", userId, sessionId);
            throw new NotFoundException("Session not found");
        }
        interviewSessionRepository.deleteById(sessionId);
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

    private List<String> usableQuestions(LlmInterviewQuestions llmQuestions, VacancyData vacancyData) {
        List<String> questions = llmQuestions.questions() == null ? List.of() : llmQuestions.questions().stream()
                .filter(q -> q != null && !q.isBlank())
                .limit(LlmInterviewQuestionsRequest.MAX_COUNT)
                .toList();
        if (questions.size() < LlmInterviewQuestionsRequest.MIN_COUNT) {
            log.error("LLM returned only {} usable interview questions, {} required [url={}]",
                    questions.size(), LlmInterviewQuestionsRequest.MIN_COUNT, vacancyData.url());
            throw new LlmException("Not enough questions for an interview session");
        }
        return questions;
    }

    private void checkAllQuestionsAnswered(InterviewSession session, List<InterviewQuestion> answered) {
        long answeredMain = answered.stream().filter(q -> !q.isFollowUp()).count();
        if (answeredMain < session.getTotalQuestions()) {
            log.warn("Interview session {} has only {} of {} answered questions, cannot finish",
                    session.getId(), answeredMain, session.getTotalQuestions());
            throw new ConflictException("Not all questions answered");
        }
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
