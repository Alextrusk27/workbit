package ru.workbit.training.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.SkillDictRepository;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingFeedback;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.mapper.TrainingReportMapper;
import ru.workbit.training.model.mapper.TrainingSessionMapper;
import ru.workbit.training.repository.TrainingQuestionRepository;
import ru.workbit.training.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.util.DictText;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static ru.workbit.training.service.TrainingSessions.answeredSorted;
import static ru.workbit.training.service.TrainingSessions.checkSessionCompleted;
import static ru.workbit.training.service.TrainingSessions.checkSessionNotCompleted;

@Component
@Slf4j
@RequiredArgsConstructor
class TrainingWriter {

    static final int MIN_OVERALL_FEEDBACK_LENGTH = 10;
    static final double MIN_REVIEWED_QUESTIONS_RATIO = 0.5;

    private final TrainingSessionRepository trainingSessionRepository;
    private final TrainingQuestionRepository trainingQuestionRepository;
    private final ProfessionDictRepository professionDictRepository;
    private final SkillDictRepository skillDictRepository;
    private final QuotaService quotaService;

    private final TrainingSessionMapper trainingSessionMapper;
    private final TrainingReportMapper trainingReportMapper;

    record DictionaryRefs(UUID professionId, UUID skillId) {
    }

    @Transactional
    public DictionaryRefs upsertDictionaries(String skill, String profession) {
        UUID professionId = professionDictRepository.upsertAndIncrementUsage(
                profession, DictText.matchKey(profession));
        UUID skillId = skillDictRepository.upsertAndIncrementUsage(
                professionId, skill, DictText.matchKey(skill));
        return new DictionaryRefs(professionId, skillId);
    }

    @Transactional
    public TrainingSessionResponse createSession(TrainingSession session, List<BankQuestion> bankQuestions,
                                                 List<String> generatedQuestions) {
        quotaService.debitTraining(session.getUserId());

        List<TrainingQuestion> questions = new ArrayList<>();
        for (BankQuestion bankQuestion : bankQuestions) {
            questions.add(buildQuestion(session, bankQuestion.getText(), bankQuestion.getId(),
                    bankQuestion.getReferenceAnswer(), questions.size() + 1));
        }
        for (String text : generatedQuestions) {
            questions.add(buildQuestion(session, text, null, null, questions.size() + 1));
        }

        session.setQuestions(questions);
        trainingSessionRepository.save(session);

        return trainingSessionMapper.toResponse(session, 0, questions.size());
    }

    @Transactional
    public TrainingSessionResponse appendQuestions(UUID sessionId, List<BankQuestion> bankQuestions,
                                                   List<String> generatedQuestions) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);

        List<TrainingQuestion> questions = session.getQuestions();
        int orderIndex = questions.stream().mapToInt(TrainingQuestion::getOrderIndex).max().orElse(0);
        for (BankQuestion bankQuestion : bankQuestions) {
            questions.add(buildQuestion(session, bankQuestion.getText(), bankQuestion.getId(),
                    bankQuestion.getReferenceAnswer(), ++orderIndex));
        }
        for (String text : generatedQuestions) {
            questions.add(buildQuestion(session, text, null, null, ++orderIndex));
        }

        trainingSessionRepository.save(session);

        int answered = (int) questions.stream().filter(TrainingQuestion::isAnswered).count();
        return trainingSessionMapper.toResponse(session, answered, questions.size());
    }

    /**
     * Перезапуск: вопросы и эталонные ответы остаются на месте, ответы, фидбэк и отчёт стираются,
     * сессия возвращается в исходное состояние.
     */
    @Transactional
    public TrainingSessionResponse restartSession(UUID sessionId) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionCompleted(session);
        quotaService.debitTraining(session.getUserId());

        for (TrainingQuestion question : session.getQuestions()) {
            question.setFeedback(null);
            question.setAnswered(false);
            question.setAnswerText(null);
            question.setAnsweredAt(null);
        }
        session.setReport(null);
        session.setStatus(TrainingSession.Status.CREATED);
        session.setCompletedAt(null);

        trainingSessionRepository.save(session);

        return trainingSessionMapper.toResponse(session, 0, session.getQuestions().size());
    }

    private static TrainingQuestion buildQuestion(TrainingSession session, String text, UUID bankQuestionId,
                                                  String referenceAnswer, int orderIndex) {
        return TrainingQuestion.builder()
                .trainingSession(session)
                .bankQuestionId(bankQuestionId)
                .text(text)
                .referenceAnswer(referenceAnswer != null && !referenceAnswer.isBlank() ? referenceAnswer : null)
                .orderIndex(orderIndex)
                .build();
    }

    @Transactional
    public void saveReferenceAnswer(UUID questionId, String answer) {
        trainingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Question not found"))
                .setReferenceAnswer(answer);
    }

    @Transactional
    public TrainingReportResponse completeReport(UUID sessionId, LlmTrainingReport llmReport) {
        TrainingSession session = trainingSessionRepository.findWithQuestionsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        checkSessionNotCompleted(session);
        checkOverallFeedback(sessionId, llmReport.overallFeedback());

        List<TrainingQuestion> answered = answeredSorted(session);
        saveFeedbacks(answered, llmReport.cases() != null ? llmReport.cases() : List.of());
        checkEnoughReviewedQuestions(sessionId, answered);

        double avgScore = calculateAvgScore(answered);

        session.getQuestions().removeIf(q -> !q.isAnswered());
        session.setReport(TrainingReport.builder()
                .trainingSession(session)
                .avgScore(avgScore)
                .overallFeedback(llmReport.overallFeedback())
                .build());
        session.setStatus(TrainingSession.Status.COMPLETED);
        session.setCompletedAt(Instant.now());

        trainingSessionRepository.save(session);

        return trainingReportMapper.toResponse(session.getReport(), session, answered);
    }

    private void saveFeedbacks(List<TrainingQuestion> answered, List<LlmTrainingCaseReview> reviews) {
        for (LlmTrainingCaseReview review : reviews) {
            if (review.index() < 1 || review.index() > answered.size()) {
                log.warn("LLM returned review with invalid index {}, skipping feedback", review.index());
                continue;
            }
            if (!isPersistableFeedback(review.score(), review.evaluation())) {
                log.warn("LLM returned invalid review for question {} (score={}), skipping feedback",
                        review.index(), review.score());
                continue;
            }

            TrainingQuestion question = answered.get(review.index() - 1);
            if (question.getFeedback() != null) {
                log.warn("LLM returned duplicate review for question {}, skipping feedback", review.index());
                continue;
            }
            question.setFeedback(TrainingFeedback.builder()
                    .question(question)
                    .score(review.score())
                    .text(review.evaluation())
                    .build());
        }
    }

    private double calculateAvgScore(List<TrainingQuestion> answered) {
        double avg = answered.stream()
                .map(TrainingQuestion::getFeedback)
                .filter(Objects::nonNull)
                .mapToInt(TrainingFeedback::getScore)
                .average()
                .orElseThrow(() -> new LlmException("Training report has no usable scores"));
        return Math.round(avg * 10) / 10.0;
    }

    private void checkEnoughReviewedQuestions(UUID sessionId, List<TrainingQuestion> answered) {
        long reviewed = answered.stream().filter(q -> q.getFeedback() != null).count();
        if (reviewed < answered.size() * MIN_REVIEWED_QUESTIONS_RATIO) {
            log.error("Cannot finish session {}: LLM reviewed only {} of {} questions",
                    sessionId, reviewed, answered.size());
            throw new LlmException("Training report has too few reviewed questions");
        }
    }

    private void checkOverallFeedback(UUID sessionId, String overallFeedback) {
        if (overallFeedback == null || overallFeedback.isBlank()
                || overallFeedback.length() < MIN_OVERALL_FEEDBACK_LENGTH) {
            log.error("Cannot finish session {}: LLM report has no usable overall feedback", sessionId);
            throw new LlmException("Training report has no usable overall feedback");
        }
    }

    private static boolean isPersistableFeedback(Integer score, String text) {
        return score != null && score >= 1 && score <= 5 && text != null && !text.isBlank();
    }
}
