package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.QuestionResponse;
import ru.workbit.interview.dto.SessionReport;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.AnswerFeedback;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.OfferProbability;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.repository.FeedbackRepository;
import ru.workbit.interview.repository.QuestionRepository;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.dto.LlmAnswerEvaluation;
import ru.workbit.llm.dto.LlmAnswerReview;
import ru.workbit.llm.dto.LlmReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewWriterTest")
class InterviewWriterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    SessionRepository sessionRepository;
    @Mock
    QuestionRepository questionRepository;
    @Mock
    FeedbackRepository feedbackRepository;
    @Mock
    QuestionMapper questionMapper;
    @Mock
    SessionMapper sessionMapper;

    @InjectMocks
    InterviewWriter interviewWriter;

    private InterviewSession.InterviewSessionBuilder aSessionBuilder() {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .userId(USER_ID)
                .profession(Profession.JAVA_DEV)
                .level(Level.MIDDLE)
                .companyType(CompanyType.PRODUCT)
                .status(SessionStatus.CREATED)
                .totalQuestions(10);
    }

    // -------------------------------------------------------------------------
    // saveAnswer
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SaveAnswer")
    class SaveAnswer {

        private final UUID questionId = UUID.randomUUID();

        private InterviewQuestion aQuestion(InterviewSession session) {
            return InterviewQuestion.builder()
                    .id(questionId)
                    .session(session)
                    .questionText("Что такое JVM?")
                    .answered(false)
                    .build();
        }

        @Test
        @DisplayName("Сохраняет ответ и переводит сессию CREATED -> IN_PROGRESS")
        void savesAnswerAndTransitionsStatus() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.CREATED).build();
            InterviewQuestion question = aQuestion(session);
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            QuestionResponse expectedResponse = new QuestionResponse(
                    questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", null, null);
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "JVM - виртуальная машина");

            // when
            var result = interviewWriter.saveAnswer(request);

            // then
            assertThat(result.response()).isEqualTo(expectedResponse);
            assertThat(result.session()).isSameAs(session);
            assertThat(result.questionText()).isEqualTo("Что такое JVM?");

            assertThat(question.isAnswered()).isTrue();
            assertThat(question.getAnswerText()).isEqualTo("JVM - виртуальная машина");
            assertThat(question.getAnsweredAt()).isNotNull();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда вопрос не найден")
        void throwsWhenQuestionNotFound() {
            // given
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());
            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "answer");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveAnswer(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");

            verifyNoInteractions(questionMapper);
        }

        @Test
        @DisplayName("Бросает ForbiddenException, когда вопрос принадлежит другому пользователю")
        void throwsWhenQuestionOwnedByAnotherUser() {
            // given
            InterviewSession session = aSessionBuilder().userId(UUID.randomUUID()).build();
            InterviewQuestion question = aQuestion(session);
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "answer");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveAnswer(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");

            verifyNoInteractions(questionMapper);
        }

        @Test
        @DisplayName("Бросает ConflictException, когда вопрос принадлежит другой сессии")
        void throwsWhenQuestionBelongsToAnotherSession() {
            // given
            InterviewSession session = aSessionBuilder().id(UUID.randomUUID()).build();
            InterviewQuestion question = aQuestion(session);
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "answer");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");

            verifyNoInteractions(questionMapper);
        }

        @Test
        @DisplayName("Бросает ConflictException и не сохраняет ответ, когда сессия уже завершена")
        void throwsWhenSessionAlreadyCompleted() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.COMPLETED).build();
            InterviewQuestion question = aQuestion(session);
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "answer");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");

            assertThat(question.isAnswered()).isFalse();
            assertThat(question.getAnswerText()).isNull();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);

            verifyNoInteractions(questionMapper, feedbackRepository);
        }

        @Test
        @DisplayName("Бросает ConflictException, когда вопрос уже отвечен")
        void throwsWhenQuestionAlreadyAnswered() {
            // given
            InterviewSession session = aSessionBuilder().build();
            InterviewQuestion question = aQuestion(session);
            question.setAnswered(true);
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "answer");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question already answered");

            verifyNoInteractions(questionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // saveFeedback
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SaveFeedback")
    class SaveFeedback {

        private final UUID questionId = UUID.randomUUID();

        private InterviewQuestion aQuestion() {
            return InterviewQuestion.builder()
                    .id(questionId)
                    .session(aSessionBuilder().build())
                    .questionText("Что такое JVM?")
                    .answerText("JVM - виртуальная машина")
                    .answered(true)
                    .build();
        }

        @Test
        @DisplayName("Создаёт фидбэк и возвращает DTO с оценкой и текстом")
        void createsFeedbackAndReturnsDto() {
            // given
            InterviewQuestion question = aQuestion();
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            when(feedbackRepository.save(any(AnswerFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

            QuestionResponse expectedResponse = new QuestionResponse(
                    questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", 4, "Хороший ответ");
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "Хороший ответ");

            // when
            var result = interviewWriter.saveFeedback(questionId, evaluation);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(question.getFeedback()).isNotNull();
            assertThat(question.getFeedback().getScore()).isEqualTo(4);
            assertThat(question.getFeedback().getFeedbackText()).isEqualTo("Хороший ответ");
            verify(feedbackRepository).save(any(AnswerFeedback.class));
        }

        @Test
        @DisplayName("Не создаёт фидбэк, когда оценка LLM вне диапазона 1..5")
        void skipsFeedbackWhenScoreOutOfRange() {
            // given
            InterviewQuestion question = aQuestion();
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            QuestionResponse expectedResponse = new QuestionResponse(
                    questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", null, null);
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(6, "Отлично");

            // when
            var result = interviewWriter.saveFeedback(questionId, evaluation);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(question.getFeedback()).isNull();
            verify(feedbackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Не создаёт фидбэк, когда текст фидбэка пустой/пробельный")
        void skipsFeedbackWhenTextBlank() {
            // given
            InterviewQuestion question = aQuestion();
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));

            QuestionResponse expectedResponse = new QuestionResponse(
                    questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", null, null);
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "  ");

            // when
            var result = interviewWriter.saveFeedback(questionId, evaluation);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(question.getFeedback()).isNull();
            verify(feedbackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда вопрос не найден")
        void throwsWhenQuestionNotFound() {
            // given
            when(questionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());
            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "Хорошо");

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveFeedback(questionId, evaluation))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");

            verifyNoInteractions(feedbackRepository, questionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // completeReport
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CompleteReport")
    class CompleteReport {

        @Test
        @DisplayName("Формирует отчёт, сохраняет фидбэки только для неоцененных вопросов и завершает сессию")
        void createsReportSavesFeedbacksForUnreviewedQuestionsAndCompletesSession() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();
            UUID q3Id = UUID.randomUUID();
            UUID q4Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();
            InterviewQuestion q3 = InterviewQuestion.builder()
                    .id(q3Id).session(session).questionText("Q3").answerText("A3").build();
            InterviewQuestion q4 = InterviewQuestion.builder()
                    .id(q4Id).session(session).questionText("Q4").answerText("A4").build();
            AnswerFeedback existingFeedback = AnswerFeedback.builder()
                    .question(q4).score(5).feedbackText("Уже оценено ранее").build();
            q4.setFeedback(existingFeedback);

            session.setQuestions(List.of(q1, q2, q3, q4));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Хорошо", 4),
                            new LlmAnswerReview(q2Id, "Хорошо", 4),
                            new LlmAnswerReview(q3Id, "Отлично", 5),
                            new LlmAnswerReview(q4Id, "Уже оценено ранее", null)
                    ),
                    "В целом уверенное собеседование.",
                    "MEDIUM"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.3, "В целом уверенное собеседование.", OfferProbability.MEDIUM, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(result).isEqualTo(expectedReport);

            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q1.getFeedback().getFeedbackText()).isEqualTo("Хорошо");
            assertThat(q2.getFeedback().getScore()).isEqualTo(4);
            assertThat(q3.getFeedback().getScore()).isEqualTo(5);
            assertThat(q3.getFeedback().getFeedbackText()).isEqualTo("Отлично");
            // фидбэк уже отвеченного вопроса не перезаписывается
            assertThat(q4.getFeedback()).isSameAs(existingFeedback);

            assertThat(session.getInterviewReport()).isNotNull();
            // (4 + 4 + 5) / 3 = 4.333..., округление до 0.1 -> 4.3; null-скор q4 отфильтрован
            assertThat(session.getInterviewReport().getAvgScore()).isEqualTo(4.3);
            assertThat(session.getInterviewReport().getOfferProbability()).isEqualTo(OfferProbability.MEDIUM);
            assertThat(session.getInterviewReport().getOverallFeedback())
                    .isEqualTo("В целом уверенное собеседование.");

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(session.getCompletedAt()).isNotNull();

            verify(sessionRepository).save(session);
            verifyNoInteractions(feedbackRepository);
        }

        @Test
        @DisplayName("Не падает с NPE и не создаёт фидбэк, когда LLM вернула отзыв без score")
        void skipsFeedbackWhenLlmReturnsNullScore() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();

            session.setQuestions(List.of(q1, q2));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Хорошо", 4),
                            new LlmAnswerReview(q2Id, "Не удалось оценить", null)
                    ),
                    "Собеседование пройдено.",
                    "LOW"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Собеседование пройдено.", OfferProbability.LOW, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(result).isEqualTo(expectedReport);

            assertThat(q1.getFeedback()).isNotNull();
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            // вопрос с null score от LLM не получает фидбэк, а не падает с NPE
            assertThat(q2.getFeedback()).isNull();

            assertThat(session.getInterviewReport()).isNotNull();
            // null-скор q2 отфильтрован, средний балл считается только по q1
            assertThat(session.getInterviewReport().getAvgScore()).isEqualTo(4.0);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(session.getCompletedAt()).isNotNull();

            verify(sessionRepository).save(session);
            verifyNoInteractions(feedbackRepository);
        }

        @Test
        @DisplayName("Не падает с NPE и не создаёт фидбэк, когда LLM вообще не вернула отзыв на вопрос")
        void skipsFeedbackWhenLlmReturnsNoReviewForQuestion() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();

            session.setQuestions(List.of(q1, q2));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            // LLM вернула отзыв только на q1, про q2 в ответе нет записи вовсе
            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)),
                    "Собеседование пройдено.",
                    "LOW"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Собеседование пройдено.", OfferProbability.LOW, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(result).isEqualTo(expectedReport);

            assertThat(q1.getFeedback()).isNotNull();
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            // на вопрос без отзыва от LLM (нет записи в answersMap) фидбэк не создаётся
            assertThat(q2.getFeedback()).isNull();

            assertThat(session.getInterviewReport()).isNotNull();
            assertThat(session.getInterviewReport().getAvgScore()).isEqualTo(4.0);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(session.getCompletedAt()).isNotNull();

            verify(sessionRepository).save(session);
            verifyNoInteractions(feedbackRepository);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда сессия не найдена")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.empty());
            LlmReport llmReport = new LlmReport(List.of(), "Отчёт", "LOW");

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(feedbackRepository, sessionMapper);
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Бросает LlmException, когда все отзывы LLM без score")
        void throwsWhenAllReviewsHaveNullScore() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();

            session.setQuestions(List.of(q1, q2));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Не удалось оценить", null),
                            new LlmAnswerReview(q2Id, "Не удалось оценить", null)
                    ),
                    "Отчёт",
                    "LOW"
            );

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable scores");

            assertThat(session.getInterviewReport()).isNull();
            assertThat(session.getStatus()).isNotEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository, never()).save(any());
            verifyNoInteractions(feedbackRepository, sessionMapper);
        }

        @Test
        @DisplayName("Пропускает фидбэк вопроса с оценкой вне диапазона 1..5, а средний балл считает только по валидным")
        void skipsFeedbackForOutOfRangeScoreAndAveragesOnlyValidOnes() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();

            session.setQuestions(List.of(q1, q2));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Хорошо", 4),
                            new LlmAnswerReview(q2Id, "Отлично", 6)
                    ),
                    "Отчёт",
                    "LOW"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Отчёт", OfferProbability.LOW, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(result).isEqualTo(expectedReport);

            assertThat(q1.getFeedback()).isNotNull();
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            // оценка вне диапазона 1..5 не сохраняется в фидбэк
            assertThat(q2.getFeedback()).isNull();

            assertThat(session.getInterviewReport()).isNotNull();
            // средний балл считается только по валидным оценкам (q2 со score=6 отфильтрован)
            assertThat(session.getInterviewReport().getAvgScore()).isEqualTo(4.0);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            verify(sessionRepository).save(session);
            verifyNoInteractions(feedbackRepository);
        }

        @Test
        @DisplayName("Бросает LlmException, когда все отзывы LLM вне диапазона 1..5")
        void throwsWhenAllReviewsHaveOutOfRangeScore() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            UUID q2Id = UUID.randomUUID();

            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            InterviewQuestion q2 = InterviewQuestion.builder()
                    .id(q2Id).session(session).questionText("Q2").answerText("A2").build();

            session.setQuestions(List.of(q1, q2));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Отлично", 6),
                            new LlmAnswerReview(q2Id, "Плохо", 0)
                    ),
                    "Отчёт",
                    "LOW"
            );

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable scores");

            assertThat(q1.getFeedback()).isNull();
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getInterviewReport()).isNull();
            assertThat(session.getStatus()).isNotEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository, never()).save(any());
            verifyNoInteractions(feedbackRepository, sessionMapper);
        }

        @Test
        @DisplayName("Бросает LlmException, когда LLM вернула пустой список отзывов")
        void throwsWhenLlmReturnsEmptyAnswersList() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();

            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(List.of(), "Отчёт", "LOW");

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable scores");

            assertThat(session.getInterviewReport()).isNull();
            assertThat(session.getStatus()).isNotEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository, never()).save(any());
            verifyNoInteractions(feedbackRepository, sessionMapper);
        }

        @Test
        @DisplayName("Бросает LlmException и не падает с NPE, когда LLM вернула null вместо списка отзывов")
        void throwsWhenLlmReturnsNullAnswersList() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();

            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(null, "Отчёт", "LOW");

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable scores");

            assertThat(session.getInterviewReport()).isNull();
            assertThat(session.getStatus()).isNotEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository, never()).save(any());
            verifyNoInteractions(feedbackRepository, sessionMapper);
        }

        @Test
        @DisplayName("Бросает LlmException, когда offerProbability от LLM не распознан")
        void throwsWhenOfferProbabilityUnrecognized() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();

            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)),
                    "Отчёт",
                    "unknown"
            );

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(SESSION_ID, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has an invalid offer probability");

            assertThat(session.getInterviewReport()).isNull();
            assertThat(session.getStatus()).isNotEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository, never()).save(any());
            verifyNoInteractions(sessionMapper);
        }

        @Test
        @DisplayName("Распознаёт русский лейбл offerProbability от LLM и завершает сессию")
        void resolvesOfferProbabilityFromRussianLabel() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();

            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)),
                    "Отчёт",
                    "Средняя"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Отчёт", OfferProbability.MEDIUM, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(session.getInterviewReport()).isNotNull();
            assertThat(session.getInterviewReport().getOfferProbability()).isEqualTo(OfferProbability.MEDIUM);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);

            verify(sessionRepository).save(session);
        }

        @Test
        @DisplayName("Не падает с дублирующимися id вопросов от LLM и дедуплицирует отзывы")
        void deduplicatesReviewsWithDuplicateQuestionIds() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();

            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(
                            new LlmAnswerReview(q1Id, "Первый", 4),
                            new LlmAnswerReview(q1Id, "Второй", 2)
                    ),
                    "Отчёт",
                    "LOW"
            );

            SessionReport expectedReport = new SessionReport(
                    null, SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Отчёт", OfferProbability.LOW, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewWriter.completeReport(SESSION_ID, llmReport);

            // then
            assertThat(result).isEqualTo(expectedReport);

            assertThat(q1.getFeedback()).isNotNull();
            // при дубликате id merge-функция оставляет первый отзыв
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q1.getFeedback().getFeedbackText()).isEqualTo("Первый");

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            verify(sessionRepository).save(session);
        }
    }
}
