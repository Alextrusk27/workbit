package ru.workbit.interview.service;

import static ru.workbit.interview.service.InterviewSessions.answeredMainSorted;
import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTopicKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class InterviewService {

    private static final int MAX_QUESTIONS_PER_CASE = 6;
    private static final String ASKED_BEFORE_HEADER = "Уже задавалось:";
    private static final TypeReference<List<LlmInterviewTopic>> PLAN_TOPICS_TYPE = new TypeReference<>() {
    };

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
    private final ObjectMapper objectMapper;

    public InterviewSessionResponse createSession(String vacancyUrl, UUID userId) {
        VacancyData vacancyData = vacancyService.fetch(vacancyUrl);
        List<UUID> snapshotIds = vacancyService.getSnapshotIds(vacancyData.sourceId());

        checkNoUnfinishedInterview(vacancyData, userId, snapshotIds);

        quotaService.checkInterviewAvailable(userId);

        String askedBefore = askedBefore(userId, snapshotIds);
        LlmInterviewPlan plan = requestPlan(vacancyData, askedBefore);

        InterviewSession session = interviewWriter.createSession(vacancyData, userId, plan, askedBefore);
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

    private void checkNoUnfinishedInterview(VacancyData vacancyData, UUID userId, List<UUID> snapshotIds) {
        if (!snapshotIds.isEmpty() && interviewSessionRepository
                .existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                        userId, snapshotIds, InterviewSession.Status.COMPLETED)) {
            log.warn("User {} already has an unfinished interview for vacancy {}", userId, vacancyData.sourceId());
            throw new ConflictException("Unfinished interview exists");
        }
    }

    /**
     * Основные вопросы прошлых интервью этого пользователя по этой вакансии - блоком для модели,
     * чтобы она их не повторяла. Собирается один раз при создании сессии и дальше живёт в ней:
     * блок идёт в кэшируемый префикс запроса и обязан быть одним и тем же на всех ходах беседы.
     */
    private String askedBefore(UUID userId, List<UUID> snapshotIds) {
        if (snapshotIds.isEmpty()) {
            return null;
        }

        List<String> questions = interviewQuestionRepository.findQuestionTexts(userId, snapshotIds,
                InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN);

        return questions.isEmpty() ? null
                : questions.stream().collect(Collectors.joining("\n- ", ASKED_BEFORE_HEADER + "\n- ", ""));
    }

    /**
     * План собеседования с одним повторным вызовом на вырожденный или несведённый ответ. План без
     * первого вопроса и после повтора - ошибка; план с несведённым распределением по темам (сумма
     * questions против questionCount, доля ядра, лимит SOFT-темы) после повтора не отвергается,
     * а приводится кодом - {@link #normalizePlan}.
     */
    private LlmInterviewPlan requestPlan(VacancyData vacancyData, String askedBefore) {
        LlmInterviewVacancy vacancy = new LlmInterviewVacancy(vacancyData.name(), vacancyData.employer(),
                vacancyData.experience(), vacancyData.keySkills(), vacancyData.description());

        LlmInterviewPlan plan = llmService.planInterview(vacancy, askedBefore);
        if (!isUsablePlan(plan) || !isConsistentPlan(plan)) {
            log.warn("LLM returned an unusable or inconsistent interview plan, retrying "
                    + "[url={}, blankQuestion={}]", vacancyData.url(), !isUsablePlan(plan));
            plan = llmService.planInterview(vacancy, askedBefore);
        }
        if (!isUsablePlan(plan)) {
            log.error("LLM returned an interview plan without the first question after retry [url={}]",
                    vacancyData.url());
            throw new LlmException("Interview plan has no first question");
        }
        if (isConsistentPlan(plan)) {
            return plan;
        }

        log.warn("LLM returned an inconsistent interview plan after retry, normalizing [url={}]",
                vacancyData.url());
        return normalizePlan(plan);
    }

    private static boolean isUsablePlan(LlmInterviewPlan plan) {
        return plan.question() != null && !plan.question().isBlank();
    }

    /**
     * Инварианты структурного плана: темы без дыр, questionCount в коридоре и равен сумме
     * questions, на ядро - не меньше половины, про отношение к работе - не больше одного вопроса
     * одной темой, тема первого вопроса есть в списке.
     */
    private static boolean isConsistentPlan(LlmInterviewPlan plan) {
        List<LlmInterviewTopic> topics = plan.topics();
        if (topics == null || topics.isEmpty() || !topics.stream().allMatch(InterviewService::isWellFormedTopic)) {
            return false;
        }

        int count = plan.questionCount();
        int sum = topics.stream().mapToInt(LlmInterviewTopic::questions).sum();
        int core = questionsOf(topics, LlmInterviewTopicKind.CORE);
        int soft = questionsOf(topics, LlmInterviewTopicKind.SOFT);

        return count >= LlmInterviewPlan.MIN_COUNT && count <= LlmInterviewPlan.MAX_COUNT
                && sum == count
                && core * 2 >= count
                && soft <= 1
                && plan.topic() != null
                && topics.stream().anyMatch(t -> t.name().equals(plan.topic()));
    }

    private static boolean isWellFormedTopic(LlmInterviewTopic topic) {
        return topic != null && topic.name() != null && !topic.name().isBlank()
                && topic.questions() != null && topic.questions() >= 1 && topic.kind() != null;
    }

    private static int questionsOf(List<LlmInterviewTopic> topics, LlmInterviewTopicKind kind) {
        return topics.stream()
                .filter(t -> t.kind() == kind)
                .mapToInt(LlmInterviewTopic::questions)
                .sum();
    }

    /**
     * Приводит несведённый план к инвариантам {@link #isConsistentPlan}: чинит вырожденные темы,
     * оставляет одну SOFT-тему с одним вопросом, гарантирует тему первого вопроса и ядро,
     * доводит долю ядра и сумму questions до коридора, а questionCount берёт из суммы.
     * План вовсе без пригодных тем возвращается по-старому: без тем, с обрезанным questionCount.
     */
    private static LlmInterviewPlan normalizePlan(LlmInterviewPlan plan) {
        List<LlmInterviewTopic> topics = new ArrayList<>();
        for (LlmInterviewTopic topic : plan.topics() == null ? List.<LlmInterviewTopic>of() : plan.topics()) {
            if (topic == null || topic.name() == null || topic.name().isBlank()) {
                continue;
            }
            int questions = topic.questions() == null || topic.questions() < 1 ? 1 : topic.questions();
            LlmInterviewTopicKind kind = topic.kind() == null ? LlmInterviewTopicKind.STANDARD : topic.kind();
            topics.add(new LlmInterviewTopic(topic.name(), questions, kind));
        }
        if (topics.isEmpty()) {
            int count = Math.clamp(plan.questionCount(), LlmInterviewPlan.MIN_COUNT, LlmInterviewPlan.MAX_COUNT);
            return new LlmInterviewPlan(count, null, plan.topic(), plan.question());
        }

        dropExtraSoft(topics);
        ensureFirstQuestionTopic(topics, plan.topic());
        ensureCore(topics, plan.topic());
        growCore(topics);
        trimTo(topics, LlmInterviewPlan.MAX_COUNT, plan.topic());

        int sum = topics.stream().mapToInt(LlmInterviewTopic::questions).sum();
        return new LlmInterviewPlan(sum, List.copyOf(topics), plan.topic(), plan.question());
    }

    /** Первая SOFT-тема остаётся с одним вопросом, остальные SOFT-темы отбрасываются. */
    private static void dropExtraSoft(List<LlmInterviewTopic> topics) {
        boolean seen = false;
        for (int i = 0; i < topics.size(); ) {
            LlmInterviewTopic topic = topics.get(i);
            if (topic.kind() != LlmInterviewTopicKind.SOFT) {
                i++;
                continue;
            }
            if (seen) {
                topics.remove(i);
                continue;
            }
            seen = true;
            topics.set(i, new LlmInterviewTopic(topic.name(), 1, topic.kind()));
            i++;
        }
    }

    private static void ensureFirstQuestionTopic(List<LlmInterviewTopic> topics, String firstTopic) {
        if (firstTopic == null || firstTopic.isBlank()
                || topics.stream().anyMatch(t -> t.name().equals(firstTopic))) {
            return;
        }
        topics.addFirst(new LlmInterviewTopic(firstTopic, 1, LlmInterviewTopicKind.CORE));
    }

    /** Без единой CORE-темы ядром назначается тема первого вопроса, а нет её в плане - первая. */
    private static void ensureCore(List<LlmInterviewTopic> topics, String firstTopic) {
        if (topics.stream().anyMatch(t -> t.kind() == LlmInterviewTopicKind.CORE)) {
            return;
        }
        int index = Math.max(indexOfName(topics, firstTopic), 0);
        LlmInterviewTopic topic = topics.get(index);
        topics.set(index, new LlmInterviewTopic(topic.name(), topic.questions(), LlmInterviewTopicKind.CORE));
    }

    /** Добавляет вопросы первой CORE-теме, пока ядро не займёт половину и сумма не дойдёт до MIN_COUNT. */
    private static void growCore(List<LlmInterviewTopic> topics) {
        int sum = topics.stream().mapToInt(LlmInterviewTopic::questions).sum();
        int core = questionsOf(topics, LlmInterviewTopicKind.CORE);
        int extra = Math.max(sum - core * 2, 0);
        extra += Math.max(LlmInterviewPlan.MIN_COUNT - (sum + extra), 0);
        if (extra == 0) {
            return;
        }

        int index = IntStream.range(0, topics.size())
                .filter(i -> topics.get(i).kind() == LlmInterviewTopicKind.CORE)
                .findFirst()
                .orElseThrow();
        LlmInterviewTopic topic = topics.get(index);
        topics.set(index, new LlmInterviewTopic(topic.name(), topic.questions() + extra, topic.kind()));
    }

    /**
     * Срезает сумму questions до лимита: с хвоста, сначала по не-CORE-темам, затем по CORE,
     * последний вопрос темы не срезается. Когда резать больше нечего, хвостовые темы
     * отбрасываются целиком; тема первого вопроса не отбрасывается никогда.
     */
    private static void trimTo(List<LlmInterviewTopic> topics, int limit, String firstTopic) {
        int sum = topics.stream().mapToInt(LlmInterviewTopic::questions).sum();
        while (sum > limit) {
            int index = lastReducible(topics, false);
            if (index < 0) {
                index = lastReducible(topics, true);
            }
            if (index >= 0) {
                LlmInterviewTopic topic = topics.get(index);
                topics.set(index, new LlmInterviewTopic(topic.name(), topic.questions() - 1, topic.kind()));
                sum--;
                continue;
            }

            for (int i = topics.size() - 1; i >= 0; i--) {
                if (!topics.get(i).name().equals(firstTopic)) {
                    sum -= topics.remove(i).questions();
                    break;
                }
            }
        }
    }

    private static int lastReducible(List<LlmInterviewTopic> topics, boolean core) {
        return IntStream.range(0, topics.size())
                .filter(i -> (topics.get(i).kind() == LlmInterviewTopicKind.CORE) == core)
                .filter(i -> topics.get(i).questions() > 1)
                .reduce((first, second) -> second)
                .orElse(-1);
    }

    private static int indexOfName(List<LlmInterviewTopic> topics, String name) {
        return IntStream.range(0, topics.size())
                .filter(i -> topics.get(i).name().equals(name))
                .findFirst()
                .orElse(-1);
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
     * новый основной), основной сверх плана завершает интервью, и текст такого хода - прощальная
     * реплика. Оборвать беседу решает модель ({@code END}); код лишь страхует от бесконечного
     * топтания на одном вопросе.
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

        if (cases.getLast().size() >= MAX_QUESTIONS_PER_CASE) {
            log.warn("Interview session {} got {} replies on one question, closing questioning",
                    session.getId(), cases.getLast().size());
            interviewWriter.closeQuestioning(answered.getId(), null);
            return Optional.empty();
        }

        LlmInterviewStep step = requestStep(session, dialog, cases.size());
        if (step.kind() == LlmInterviewStepKind.END) {
            interviewWriter.closeQuestioning(answered.getId(), step.question());
            return Optional.empty();
        }

        InterviewQuestion.Kind kind = resolveKind(step.kind(), cases.getLast());
        if (isFinalStep(kind, session.getTotalQuestions(), cases.size())) {
            interviewWriter.closeQuestioning(answered.getId(), closingRemark(step));
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
     * Запрос реплики с одним повторным вызовом на вырожденный ответ: пустой текст допустим у
     * {@code MAIN}, когда основные исчерпаны, и у {@code END} - в обоих случаях беседа кончилась
     * и текст нужен лишь на прощание.
     */
    private LlmInterviewStep requestStep(InterviewSession session, List<InterviewQuestion> dialog, int mainAsked) {
        VacancySnapshotView vacancy = vacancyService.getSnapshotView(session.getVacancySnapshotId());

        LlmInterviewVacancy llmVacancy = new LlmInterviewVacancy(vacancy.name(), vacancy.employer(),
                vacancy.experience(), vacancy.keySkills(), vacancy.description());

        LlmInterviewPlan plan = new LlmInterviewPlan(session.getTotalQuestions(), readPlanTopics(session),
                dialog.getFirst().getTopic(), dialog.getFirst().getText());

        List<LlmInterviewTurn> history = IntStream.range(0, dialog.size() - 1)
                .mapToObj(i -> new LlmInterviewTurn(dialog.get(i).getAnswerText(), toStep(dialog.get(i + 1))))
                .toList();

        String lastAnswer = dialog.getLast().getAnswerText();

        LlmInterviewStep step = llmService.nextInterviewStep(llmVacancy, plan, history, lastAnswer,
                session.getAskedBefore());
        if (isUsableStep(step, mainAsked, session.getTotalQuestions())) {
            return step;
        }

        log.warn("LLM returned degenerate interview step for session {}, retrying once "
                        + "[kind={}, blankQuestion={}, mainAsked={}/{}]", session.getId(), step.kind(),
                isBlank(step.question()), mainAsked, session.getTotalQuestions());

        step = llmService.nextInterviewStep(llmVacancy, plan, history, lastAnswer, session.getAskedBefore());
        if (isUsableStep(step, mainAsked, session.getTotalQuestions())) {
            return step;
        }

        log.error("LLM returned degenerate interview step for session {} after retry "
                        + "[kind={}, blankQuestion={}, mainAsked={}/{}]", session.getId(), step.kind(),
                isBlank(step.question()), mainAsked, session.getTotalQuestions());

        throw new LlmException("Interview step has no question");
    }

    /**
     * Темы плана из сессии: JSON из БД разбирается в объекты и дальше сериализуется тем же
     * ObjectMapper, что писал первый ход, - так восстановленный план совпадает байт в байт
     * и кэш промпта не промахивается.
     */
    private List<LlmInterviewTopic> readPlanTopics(InterviewSession session) {
        String json = session.getPlanTopics();
        return json == null || json.isBlank() ? null : objectMapper.readValue(json, PLAN_TOPICS_TYPE);
    }

    private static LlmInterviewStep toStep(InterviewQuestion question) {
        return new LlmInterviewStep(
                LlmInterviewStepKind.valueOf(question.getKind().name()),
                question.getTopic(),
                question.getText());
    }

    /**
     * Прощальная реплика берётся только у {@code MAIN}: там модель прощается сама. Уточнение,
     * которое код переквалифицировал в основной вопрос и тут же отбросил, прощанием не является.
     */
    private static String closingRemark(LlmInterviewStep step) {
        return step.kind() == LlmInterviewStepKind.MAIN ? step.question() : null;
    }

    private static boolean isUsableStep(LlmInterviewStep step, int mainAsked, int totalQuestions) {
        if (step.kind() == null) {
            return false;
        }


        return !isBlank(step.question())
                || step.kind() == LlmInterviewStepKind.END
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
            case END -> throw new IllegalStateException("END is handled before kind resolution");
        };
    }

    private static boolean isFinalStep(InterviewQuestion.Kind kind, int totalQuestions, int mainAsked) {
        return kind == InterviewQuestion.Kind.MAIN && mainAsked >= totalQuestions;
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
     * Запрос отчёта с одним повторным вызовом на вырожденный ответ: пустой итог или разборы не на
     * все кейсы. Итоговую валидацию делает completeReport.
     */
    private LlmInterviewReport requestReport(UUID sessionId, VacancySnapshotView vacancy,
                                             List<List<InterviewQuestion>> cases) {

        LlmInterviewVacancy llmVacancy = new LlmInterviewVacancy(vacancy.name(), vacancy.employer(),
                vacancy.experience(), vacancy.keySkills(), vacancy.description());

        List<LlmInterviewAnswer> answers = IntStream.range(0, cases.size())
                .mapToObj(i -> toLlmAnswer(i + 1, cases.get(i)))
                .toList();

        LlmInterviewReport report = llmService.createInterviewReport(llmVacancy, answers);
        if (isUsableReport(report, cases.size())) {
            return report;
        }
        log.warn("LLM returned degenerate interview report for session {}, retrying once", sessionId);
        return llmService.createInterviewReport(llmVacancy, answers);
    }

    private static boolean isUsableReport(LlmInterviewReport report, int casesCount) {
        return report.overallFeedback() != null
                && !report.overallFeedback().isBlank()
                && report.overallFeedback().length() >= InterviewWriter.MIN_OVERALL_FEEDBACK_LENGTH
                && report.offerProbability() != null
                && report.answers() != null
                && report.answers().size() >= casesCount * InterviewWriter.MIN_REVIEWED_ANSWERS_RATIO;
    }

    private static LlmInterviewAnswer toLlmAnswer(int index, List<InterviewQuestion> interviewCase) {
        return new LlmInterviewAnswer(
                index,
                interviewCase.getFirst().getTopic(),
                interviewCase.getFirst().getText(),
                interviewCase.getFirst().getAnswerText(),
                interviewCase.stream()
                        .skip(1)
                        .map(q -> new LlmInterviewFollowUp(
                                LlmInterviewStepKind.valueOf(q.getKind().name()),
                                q.getText(),
                                q.getAnswerText()))
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
