package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.InternalServerException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.*;
import ru.workbit.interview.model.*;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.question.BankQuestion;
import ru.workbit.interview.question.QuestionBank;
import ru.workbit.interview.repository.QuestionRepository;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.dto.*;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {
    private static final int MAX_VACANCY_NAME_LENGTH = 255;

    private final QuestionBank questionBank;
    private final LlmService llmService;
    private final VacancyService vacancyService;
    private final VacancySessionCreator vacancySessionCreator;

    private final SessionMapper sessionMapper;
    private final QuestionMapper questionMapper;

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;

    private final InterviewWriter interviewWriter;

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

        return sessionMapper.toResponse(session, 0, null);
    }

    public SessionResponse createSessionByVacancy(CreateSessionByVacancyRequest request, UUID userId) {
        VacancyData data = resolveVacancyData(request);

        LlmGeneratedQuestions generated = llmService.generateVacancyQuestions(
                toGenerationRequest(data, request.totalQuestions()));

        List<String> questions = trimQuestions(generated.questions(), request.totalQuestions());
        if (questions.isEmpty()) {
            log.error("Question generator returned no questions for a vacancy session of user {}", userId);
            throw new LlmException("Question generator returned no questions");
        }

        String name = clampName(resolveName(data, generated));
        InterviewSession session = vacancySessionCreator.persist(data, name, questions, userId);

        return sessionMapper.toResponse(session, 0,
                new VacancySnapshotView(name, data.employer(), data.url(), data.experience()));
    }

    private VacancyData resolveVacancyData(CreateSessionByVacancyRequest request) {
        if (request.vacancyUrl() != null && !request.vacancyUrl().isBlank()) {
            return vacancyService.fetch(request.vacancyUrl());
        }
        return vacancyService.fromText(request.vacancyText());
    }

    private LlmQuestionGenerationRequest toGenerationRequest(VacancyData data, int questionCount) {
        return new LlmQuestionGenerationRequest(
                Objects.requireNonNullElse(data.name(), ""),
                Objects.requireNonNullElse(data.employer(), ""),
                Objects.requireNonNullElse(data.experience(), ""),
                data.keySkills() == null ? "" : String.join(", ", data.keySkills()),
                data.description(),
                questionCount);
    }

    private List<String> trimQuestions(List<String> questions, int requested) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<String> valid = questions.stream()
                .filter(q -> q != null && !q.isBlank())
                .toList();
        if (valid.size() > requested) {
            return List.copyOf(valid.subList(0, requested));
        }
        if (valid.size() < requested) {
            log.warn("LLM generated {} usable questions, {} requested; using what was generated",
                    valid.size(), requested);
        }
        return valid;
    }

    private String resolveName(VacancyData data, LlmGeneratedQuestions generated) {
        if (data.name() != null && !data.name().isBlank()) {
            return data.name();
        }
        return generated.title() != null && !generated.title().isBlank() ? generated.title() : "Вакансия";
    }

    private static String clampName(String name) {
        return name.length() > MAX_VACANCY_NAME_LENGTH ? name.substring(0, MAX_VACANCY_NAME_LENGTH) : name;
    }

    public List<SessionResponse> getAllSessions(UUID userId) {
        List<InterviewSession> sessions = sessionRepository.findAllByUserId(userId);

        Map<UUID, Long> answered = questionRepository
                .countAnsweredBySessionIds(sessions.stream().map(InterviewSession::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        QuestionRepository.AnsweredCount::getSessionId,
                        QuestionRepository.AnsweredCount::getCount));

        Map<UUID, VacancySnapshotView> vacancies = vacancyService.getSnapshotViews(
                sessions.stream()
                        .map(InterviewSession::getVacancySnapshotId)
                        .filter(Objects::nonNull)
                        .toList());

        return sessions.stream()
                .map(session -> sessionMapper.toResponse(
                        session,
                        answered.getOrDefault(session.getId(), 0L).intValue(),
                        session.getVacancySnapshotId() == null
                                ? null
                                : vacancies.get(session.getVacancySnapshotId())))
                .toList();
    }

    public SessionResponse getSession(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> sessionMapper.toResponse(
                        session,
                        (int) questionRepository.countBySessionIdAndAnsweredTrue(sessionId),
                        vacancyView(session)))
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    private VacancySnapshotView vacancyView(InterviewSession session) {
        if (session.getSource() != SessionSource.VACANCY || session.getVacancySnapshotId() == null) {
            return null;
        }
        return vacancyService.getSnapshotView(session.getVacancySnapshotId());
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

    public QuestionResponse submitAnswer(SubmitAnswerRequest request) {
        InterviewWriter.AnswerContext context = interviewWriter.saveAnswer(request);
        if (!request.evaluate()) {
            return context.response();
        }

        SessionContext sessionContext = resolveContext(context.session());
        LlmAnswerEvaluation evaluation = llmService.evaluateAnswer(
                new LlmAnswerEvaluationRequest(
                        sessionContext.profession(),
                        context.questionText(),
                        sessionContext.level(),
                        request.answerText()));

        return interviewWriter.saveFeedback(request.questionId(), evaluation);
    }

    public SessionReport finishSession(UUID sessionId, UUID userId) {
        InterviewSession session = sessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        checkSessionOwnership(session, userId);
        checkSessionNotCompleted(session);

        LlmReport llmReport = createLlmReport(session);

        return interviewWriter.completeReport(sessionId, llmReport);
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

    private void checkSessionNotCompleted(InterviewSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            log.warn("Cannot finish session {}, because it is already completed", session.getId());
            throw new ConflictException("Session already finished");
        }
    }

    private void checkUserSession(UUID sessionId, UUID userId) {
        if (!sessionRepository.existsByIdAndUserId(sessionId, userId)) {
            log.warn("User {} has no session with Id {}", userId, sessionId);
            throw new NotFoundException("Session not found");
        }
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

    private LlmReport createLlmReport(InterviewSession session) {
        SessionContext context = resolveContext(session);
        return llmService.createReport(
                new LlmReportRequest(
                        context.profession(),
                        context.level(),
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

    private SessionContext resolveContext(InterviewSession session) {
        if (session.getSource() == SessionSource.VACANCY) {
            VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());
            return new SessionContext(vacancy.name(), experienceLabel(vacancy.experience()));
        }
        return new SessionContext(session.getProfession().getName(), session.getLevel().getName());
    }

    private static String experienceLabel(String experience) {
        return experience != null && !experience.isBlank() ? experience : "не указан";
    }

    private record SessionContext(String profession, String level) {
    }
}
