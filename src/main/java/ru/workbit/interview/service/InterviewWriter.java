package ru.workbit.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.service.VacancyService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static ru.workbit.interview.service.InterviewSessions.answeredSorted;
import static ru.workbit.interview.service.InterviewSessions.checkSessionNotCompleted;
import static ru.workbit.interview.service.InterviewSessions.groupCases;

@Component
@Slf4j
@RequiredArgsConstructor
public class InterviewWriter {

    private static final int MIN_OVERALL_FEEDBACK_LENGTH = 10;
    private static final double MIN_REVIEWED_ANSWERS_RATIO = 0.5;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;

    private final VacancyService vacancyService;

    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewReportMapper interviewReportMapper;

    @Transactional
    public InterviewSession createSession(VacancyData vacancyData, UUID userId, List<String> questions) {

        UUID vacancySnapshotId = vacancyService.saveSnapshot(vacancyData);
        InterviewSession session = saveNewSession(userId, questions, vacancySnapshotId);
        attachQuestions(questions, session);

        return session;
    }

    @Transactional
    public void markFollowUpChecked(UUID questionId) {
        interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"))
                .setFollowUpChecked(true);
    }

    @Transactional
    public InterviewQuestionResponse saveFollowUp(UUID answeredQuestionId, UUID caseMainId, String text) {
        InterviewQuestion answered = interviewQuestionRepository.findWithSessionById(answeredQuestionId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        InterviewSession session = answered.getSession();
        checkSessionNotCompleted(session);
        answered.setFollowUpChecked(true);

        Optional<InterviewQuestion> pending = interviewQuestionRepository
                .findNextUnansweredFollowUp(session.getId());
        if (pending.isPresent()) {
            log.warn("Interview session {} already has an unanswered follow-up, discarding the generated one",
                    session.getId());
            return interviewQuestionMapper.toDto(pending.get());
        }

        InterviewQuestion followUp = interviewQuestionRepository.save(InterviewQuestion.builder()
                .session(session)
                .parentQuestionId(caseMainId)
                .text(text)
                .orderIndex((int) interviewQuestionRepository.countByParentQuestionId(caseMainId) + 1)
                .followUp(true)
                .build());

        return interviewQuestionMapper.toDto(followUp);
    }

    @Transactional
    public InterviewReportResponse completeReport(UUID sessionId, LlmInterviewReport llmReport) {
        InterviewSession session = interviewSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);
        checkOverallFeedback(sessionId, llmReport.overallFeedback());
        InterviewReport.OfferProbability offerProbability = parseOfferProbability(sessionId, llmReport);

        List<List<InterviewQuestion>> cases = groupCases(answeredSorted(session));
        saveFeedbacks(cases, llmReport.answers() != null ? llmReport.answers() : List.of());
        checkEnoughReviewed(sessionId, cases);

        List<InterviewQuestion> mains = cases.stream().map(List::getFirst).toList();
        double avgScore = calculateAvgScore(mains);

        session.getQuestions().removeIf(q -> !q.isAnswered() || q.isFollowUp());
        session.setReport(InterviewReport.builder()
                .session(session)
                .avgScore(avgScore)
                .offerProbability(offerProbability)
                .overallFeedback(llmReport.overallFeedback())
                .recommendations(normalizeRecommendations(llmReport.recommendations()))
                .build());
        session.setStatus(InterviewSession.Status.COMPLETED);
        session.setCompletedAt(Instant.now());

        interviewSessionRepository.save(session);

        return interviewReportMapper.toResponse(session.getReport(), session, mains);
    }

    private InterviewSession saveNewSession(UUID userId, List<String> questions, UUID vacancySnapshotId) {
        return interviewSessionRepository.save(
                InterviewSession.builder()
                        .userId(userId)
                        .totalQuestions(questions.size())
                        .vacancySnapshotId(vacancySnapshotId)
                        .build()
        );
    }

    private void attachQuestions(List<String> questions, InterviewSession session) {
        session.setQuestions(
                IntStream.range(0, questions.size())
                        .mapToObj(i -> InterviewQuestion.builder()
                                .session(session)
                                .text(questions.get(i))
                                .orderIndex(i + 1)
                                .build())
                        .toList()
        );
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
        return InterviewReport.OfferProbability.fromString(llmReport.offerProbability())
                .orElseThrow(() -> {
                    log.error("Cannot finish interview session {}: LLM returned invalid offer probability '{}'",
                            sessionId, llmReport.offerProbability());
                    return new LlmException("Interview report has no usable offer probability");
                });
    }

    private static boolean isPersistableFeedback(Integer score, String text) {
        return score != null && score >= 1 && score <= 5 && text != null && !text.isBlank();
    }

    private static String normalizeRecommendations(String recommendations) {
        return recommendations == null || recommendations.isBlank() ? null : recommendations;
    }
}
