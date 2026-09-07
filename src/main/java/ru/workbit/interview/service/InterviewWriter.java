package ru.workbit.interview.service;

import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.model.InterviewFeedback;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.mapper.InterviewQuestionMapper;
import ru.workbit.interview.model.mapper.InterviewReportMapper;
import ru.workbit.interview.repository.InterviewQuestionRepository;
import ru.workbit.interview.repository.InterviewSessionRepository;
import ru.workbit.llm.dto.LlmInterviewAnswerReview;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.service.VacancyService;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class InterviewWriter {

    static final int MIN_OVERALL_FEEDBACK_LENGTH = 10;
    static final double MIN_REVIEWED_ANSWERS_RATIO = 0.5;
    private static final int MAX_WEAKEST_SKILL_LENGTH = 100;
    private static final int FIRST_QUESTION_ORDER_INDEX = 1;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;

    private final VacancyService vacancyService;
    private final QuotaService quotaService;

    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public InterviewSession createSession(VacancyData vacancyData, UUID userId, LlmInterviewPlan plan) {
        quotaService.debitInterview(userId, "Интервью — " + vacancyData.name());

        UUID vacancySnapshotId = vacancyService.saveSnapshot(vacancyData);
        InterviewSession session = saveNewSession(userId, plan, vacancySnapshotId);
        attachFirstQuestion(plan, session);

        return session;
    }

    /**
     * Сохраняет очередной вопрос беседы: {@code MAIN} со следующим порядковым номером либо ребёнка
     * текущего основного вопроса. Отвеченный вопрос помечается проверенным - по нему модель уже сходила.
     */
    @Transactional
    public Optional<InterviewQuestionResponse> saveStep(UUID answeredQuestionId, InterviewQuestion.Kind kind,
                                                        String text, String topic) {
        InterviewQuestion answered = interviewQuestionRepository.findWithSessionById(answeredQuestionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        InterviewSession session = answered.getSession();
        checkSessionNotCompleted(session);

        if (answered.isFollowUpChecked()) {
            log.warn("Interview session {} already got the next question from a parallel request, "
                    + "discarding the generated one", session.getId());
            return interviewQuestionRepository.findNextUnanswered(session.getId())
                    .map(interviewQuestionMapper::toDto);
        }
        answered.setFollowUpChecked(true);

        UUID parentQuestionId = kind == InterviewQuestion.Kind.MAIN ? null : caseIdOf(answered);

        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.builder()
                .session(session)
                .parentQuestionId(parentQuestionId)
                .text(text)
                .topic(topic)
                .kind(kind)
                .orderIndex(nextOrderIndex(session.getId(), parentQuestionId))
                .followUp(parentQuestionId != null)
                .build());

        return Optional.of(interviewQuestionMapper.toDto(question));
    }

    /**
     * Завершает опрос: очередной вопрос не задаётся, а число основных вопросов сессии подрезается до
     * фактически отвеченных - иначе досрочный конец беседы не дал бы собрать отчёт. Прощальную реплику
     * интервьюера, если беседу оборвал он, кандидат увидит в сессии.
     */
    @Transactional
    public void closeQuestioning(UUID answeredQuestionId, String closingRemark) {
        InterviewQuestion answered = interviewQuestionRepository.findWithSessionById(answeredQuestionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        answered.setFollowUpChecked(true);

        InterviewSession session = answered.getSession();
        if (closingRemark != null && !closingRemark.isBlank()) {
            session.setClosingRemark(closingRemark.trim());
        }

        int answeredMain = (int) interviewQuestionRepository
                .countBySessionIdAndFollowUpFalseAndAnsweredTrue(session.getId());

        if (answeredMain > 0 && answeredMain < session.getTotalQuestions()) {
            log.info("Interview session {} ends after {} of {} main questions",
                    session.getId(), answeredMain, session.getTotalQuestions());
            session.setTotalQuestions(answeredMain);
        }
    }

    @Transactional
    public InterviewReportResponse completeReport(UUID sessionId, LlmInterviewReport llmReport) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);
        checkOverallFeedback(sessionId, llmReport.overallFeedback());
        final InterviewReport.OfferProbability offerProbability = parseOfferProbability(sessionId, llmReport);

        List<List<InterviewQuestion>> cases = groupCases(answeredSorted(session));
        saveFeedbacks(cases, llmReport.answers() != null ? llmReport.answers() : List.of());
        checkEnoughReviewed(sessionId, cases);

        List<InterviewQuestion> mains = cases.stream().map(List::getFirst).toList();
        double avgScore = calculateAvgScore(mains);

        session.getQuestions().removeIf(q -> !q.isAnswered());
        session.setReport(InterviewReport.builder()
                .session(session)
                .avgScore(avgScore)
                .offerProbability(offerProbability)
                .overallFeedback(llmReport.overallFeedback())
                .recommendations(normalizeRecommendations(llmReport.recommendations()))
                .weakestSkill(normalizeWeakestSkill(sessionId, llmReport.weakestSkill()))
                .build());
        session.setStatus(InterviewSession.Status.COMPLETED);
        session.setCompletedAt(Instant.now());

        interviewSessionRepository.save(session);

        return interviewReportMapper.toResponse(session.getReport(), session, mains);
    }

    private int nextOrderIndex(UUID sessionId, UUID parentQuestionId) {
        return parentQuestionId == null
                ? (int) interviewQuestionRepository.countBySessionIdAndKind(sessionId, InterviewQuestion.Kind.MAIN) + 1
                : interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(parentQuestionId).size() + 1;
    }

    private InterviewSession saveNewSession(UUID userId, LlmInterviewPlan plan, UUID vacancySnapshotId) {
        return interviewSessionRepository.save(
                InterviewSession.builder()
                        .userId(userId)
                        .totalQuestions(plan.questionCount())
                        .planTopics(plan.topics() == null ? null : objectMapper.writeValueAsString(plan.topics()))
                        .vacancySnapshotId(vacancySnapshotId)
                        .build()
        );
    }

    private void attachFirstQuestion(LlmInterviewPlan plan, InterviewSession session) {
        session.setQuestions(List.of(
                InterviewQuestion.builder()
                        .session(session)
                        .text(plan.question())
                        .topic(plan.topic())
                        .kind(InterviewQuestion.Kind.MAIN)
                        .orderIndex(FIRST_QUESTION_ORDER_INDEX)
                        .build()
        ));
    }

    private void saveFeedbacks(List<List<InterviewQuestion>> cases, List<LlmInterviewAnswerReview> reviews) {
        for (LlmInterviewAnswerReview review : reviews) {
            if (review.index() < 1 || review.index() > cases.size()) {
                log.warn("LLM returned review with invalid index {}, skipping feedback", review.index());
                continue;
            }
            if (!isPersistableFeedback(review.score(), review.evaluation())) {
                log.warn("LLM returned invalid review for answer {} (score={}), skipping feedback",
                        review.index(), review.score());
                continue;
            }

            InterviewQuestion question = cases.get(review.index() - 1).getFirst();
            if (question.getFeedback() != null) {
                log.warn("LLM returned duplicate review for answer {}, skipping feedback", review.index());
                continue;
            }
            question.setFeedback(InterviewFeedback.builder()
                    .question(question)
                    .score(review.score())
                    .text(review.evaluation())
                    .build());
        }
    }

    private double calculateAvgScore(List<InterviewQuestion> mains) {
        double avg = mains.stream()
                .map(InterviewQuestion::getFeedback)
                .filter(Objects::nonNull)
                .mapToInt(InterviewFeedback::getScore)
                .average()
                .orElseThrow(() -> new LlmException("Interview report has no usable scores"));
        return Math.round(avg * 10) / 10.0;
    }

    private void checkEnoughReviewed(UUID sessionId, List<List<InterviewQuestion>> cases) {
        long reviewed = cases.stream().filter(c -> c.getFirst().getFeedback() != null).count();
        if (reviewed < cases.size() * MIN_REVIEWED_ANSWERS_RATIO) {
            log.error("Cannot finish interview session {}: LLM reviewed only {} of {} answers",
                    sessionId, reviewed, cases.size());
            throw new LlmException("Interview report has too few reviewed answers");
        }
    }

    private void checkOverallFeedback(UUID sessionId, String overallFeedback) {
        if (overallFeedback == null || overallFeedback.isBlank()
                || overallFeedback.length() < MIN_OVERALL_FEEDBACK_LENGTH) {
            log.error("Cannot finish interview session {}: LLM report has no usable overall feedback", sessionId);
            throw new LlmException("Interview report has no usable overall feedback");
        }
    }

    private InterviewReport.OfferProbability parseOfferProbability(UUID sessionId, LlmInterviewReport llmReport) {
        if (llmReport.offerProbability() == null) {
            log.error("Cannot finish interview session {}: LLM returned no offer probability", sessionId);
            throw new LlmException("Interview report has no usable offer probability");
        }

        return InterviewReport.OfferProbability.valueOf(llmReport.offerProbability().name());
    }

    private static UUID caseIdOf(InterviewQuestion answered) {
        return answered.getKind() == InterviewQuestion.Kind.MAIN
                ? answered.getId()
                : answered.getParentQuestionId();
    }

    private static boolean isPersistableFeedback(Integer score, String text) {
        return score != null && score >= 1 && score <= 5 && text != null && !text.isBlank();
    }

    private static String normalizeRecommendations(String recommendations) {
        return recommendations == null || recommendations.isBlank() ? null : recommendations;
    }

    private static String normalizeWeakestSkill(UUID sessionId, String weakestSkill) {
        if (weakestSkill == null || weakestSkill.isBlank()) {
            return null;
        }
        String trimmed = weakestSkill.trim();
        if (trimmed.length() > MAX_WEAKEST_SKILL_LENGTH) {
            log.warn("LLM returned too long weakest skill for interview session {}, skipping it", sessionId);
            return null;
        }
        return trimmed;
    }
}
