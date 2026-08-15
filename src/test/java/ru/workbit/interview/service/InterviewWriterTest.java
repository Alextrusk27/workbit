package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
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
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.service.VacancyService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewWriterTest")
class InterviewWriterTest {

    private static final VacancyData VACANCY_DATA = new VacancyData(
            VacancySnapshot.Source.HH, "123", "https://hh.ru/vacancy/123",
            "Java-разработчик", "Работодатель", "От 3 до 6 лет", List.of("Java", "Spring"), "Описание");

    @Mock
    InterviewSessionRepository interviewSessionRepository;
    @Mock
    InterviewQuestionRepository interviewQuestionRepository;
    @Mock
    VacancyService vacancyService;
    @Mock
    QuotaService quotaService;
    @Mock
    InterviewQuestionMapper interviewQuestionMapper;
    @Mock
    InterviewReportMapper interviewReportMapper;

    @InjectMocks
    InterviewWriter interviewWriter;

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Сохраняет снапшот вакансии, сессию и основные вопросы с orderIndex 1..N")
        void savesSnapshotSessionAndQuestionsInOrder() {
            // given
            UUID userId = UUID.randomUUID();
            UUID vacancySnapshotId = UUID.randomUUID();
            List<String> questions = List.of("Вопрос 1", "Вопрос 2", "Вопрос 3");
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(vacancySnapshotId);
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            InterviewSession result = interviewWriter.createSession(VACANCY_DATA, userId, questions);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getVacancySnapshotId()).isEqualTo(vacancySnapshotId);
            assertThat(result.getTotalQuestions()).isEqualTo(3);

            List<InterviewQuestion> savedQuestions = result.getQuestions();
            assertThat(savedQuestions).hasSize(3);
            for (int i = 0; i < 3; i++) {
                InterviewQuestion question = savedQuestions.get(i);
                assertThat(question.getText()).isEqualTo(questions.get(i));
                assertThat(question.getOrderIndex()).isEqualTo(i + 1);
                assertThat(question.isFollowUp()).isFalse();
                assertThat(question.getSession()).isSameAs(result);
            }

            verify(interviewSessionRepository).save(result);
            verify(quotaService).debitInterview(userId, "Интервью — " + VACANCY_DATA.name());
        }

        @Test
        @DisplayName("Пустой список вопросов - сохраняет сессию с totalQuestions=0 и пустым списком вопросов")
        void emptyQuestionsSavesSessionWithEmptyList() {
            // given
            UUID userId = UUID.randomUUID();
            UUID vacancySnapshotId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(vacancySnapshotId);
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            InterviewSession result = interviewWriter.createSession(VACANCY_DATA, userId, List.of());

            // then
            assertThat(result.getTotalQuestions()).isZero();
            assertThat(result.getQuestions()).isEmpty();
        }

        @Test
        @DisplayName("Списывает интервью с label «Интервью — {название вакансии}»")
        void debitsInterviewWithFormattedLabel() {
            // given
            UUID userId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(UUID.randomUUID());
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            interviewWriter.createSession(VACANCY_DATA, userId, List.of("Вопрос 1"));

            // then
            verify(quotaService).debitInterview(userId, "Интервью — Java-разработчик");
        }
    }

    @Nested
    @DisplayName("MarkFollowUpChecked")
    class MarkFollowUpChecked {

        @Test
        @DisplayName("Вопрос найден - проставляет followUpChecked=true")
        void setsFollowUpCheckedFlag() {
            // given
            UUID questionId = UUID.randomUUID();
            InterviewQuestion question = InterviewQuestion.builder().id(questionId).followUpChecked(false).build();
            when(interviewQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));

            // when
            interviewWriter.markFollowUpChecked(questionId);

            // then
            assertThat(question.isFollowUpChecked()).isTrue();
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            UUID questionId = UUID.randomUUID();
            when(interviewQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewWriter.markFollowUpChecked(questionId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
        }
    }

    @Nested
    @DisplayName("SaveFollowUp")
    class SaveFollowUp {

        @Test
        @DisplayName("Нормальный путь - сохраняет follow-up с orderIndex = countByParentQuestionId + 1, проставляет followUpChecked отвеченному")
        void savesNewFollowUpWithNextOrderIndex() {
            // given
            UUID answeredId = UUID.randomUUID();
            UUID caseMainId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder().id(UUID.randomUUID()).build();
            InterviewQuestion answered = InterviewQuestion.builder()
                    .id(answeredId).session(session).text("Вопрос").orderIndex(1).followUpChecked(false).build();
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(session.getId())).thenReturn(Optional.empty());
            when(interviewQuestionRepository.countByParentQuestionId(caseMainId)).thenReturn(2L);
            when(interviewQuestionRepository.save(any(InterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

            InterviewQuestionResponse expectedResponse = new InterviewQuestionResponse(
                    UUID.randomUUID(), 3, "Новое уточнение", true, null, null, null);
            when(interviewQuestionMapper.toDto(any(InterviewQuestion.class))).thenReturn(expectedResponse);

            // when
            InterviewQuestionResponse result = interviewWriter.saveFollowUp(answeredId, caseMainId, "Новое уточнение");

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(answered.isFollowUpChecked()).isTrue();

            ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(interviewQuestionRepository).save(captor.capture());
            InterviewQuestion saved = captor.getValue();
            assertThat(saved.getOrderIndex()).isEqualTo(3);
            assertThat(saved.getParentQuestionId()).isEqualTo(caseMainId);
            assertThat(saved.getText()).isEqualTo("Новое уточнение");
            assertThat(saved.isFollowUp()).isTrue();
            assertThat(saved.getSession()).isSameAs(session);
        }

        @Test
        @DisplayName("Гонка - findNextUnansweredFollowUp уже нашёл ожидающий follow-up - возвращает его, новый не сохраняется")
        void returnsExistingPendingFollowUpInsteadOfSavingNew() {
            // given
            UUID answeredId = UUID.randomUUID();
            UUID caseMainId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder().id(UUID.randomUUID()).build();
            InterviewQuestion answered = InterviewQuestion.builder()
                    .id(answeredId).session(session).text("Вопрос").orderIndex(1).followUpChecked(false).build();
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            InterviewQuestion pendingFollowUp = InterviewQuestion.builder()
                    .id(UUID.randomUUID()).text("Уже создано").orderIndex(1).followUp(true).build();
            when(interviewQuestionRepository.findNextUnansweredFollowUp(session.getId()))
                    .thenReturn(Optional.of(pendingFollowUp));

            InterviewQuestionResponse expectedResponse = new InterviewQuestionResponse(
                    pendingFollowUp.getId(), 1, "Уже создано", true, null, null, null);
            when(interviewQuestionMapper.toDto(pendingFollowUp)).thenReturn(expectedResponse);

            // when
            InterviewQuestionResponse result = interviewWriter.saveFollowUp(answeredId, caseMainId, "Новый вопрос");

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(answered.isFollowUpChecked()).isTrue();
            verify(interviewQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Отвеченный вопрос не найден - NotFoundException")
        void throwsWhenAnsweredQuestionNotFound() {
            // given
            UUID answeredId = UUID.randomUUID();
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveFollowUp(answeredId, UUID.randomUUID(), "Уточнение"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID answeredId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder()
                    .id(UUID.randomUUID()).status(InterviewSession.Status.COMPLETED).build();
            InterviewQuestion answered = InterviewQuestion.builder()
                    .id(answeredId).session(session).text("Вопрос").orderIndex(1).build();
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveFollowUp(answeredId, UUID.randomUUID(), "Уточнение"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verify(interviewQuestionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("CompleteReport")
    class CompleteReport {

        private static final String OVERALL_FEEDBACK = "Общий развёрнутый фидбэк по интервью";

        private InterviewQuestion answeredMain(int orderIndex) {
            return InterviewQuestion.builder()
                    .id(UUID.randomUUID())
                    .text("Вопрос " + orderIndex)
                    .answerText("Ответ " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(false)
                    .answered(true)
                    .build();
        }

        @Test
        @DisplayName("Валидный отчёт - проставляет фидбэки по кейсам, средний балл, offerProbability, "
                + "recommendations, переводит сессию в COMPLETED")
        void completesSessionWithValidReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4),
                            new LlmInterviewAnswerReview(2, "Отлично", 5)),
                    "Высокая", OVERALL_FEEDBACK, "Подтянуть алгоритмы", null);

            InterviewReportResponse expectedResponse = new InterviewReportResponse(
                    UUID.randomUUID(), sessionId, 4.5, InterviewReport.OfferProbability.HIGH,
                    OVERALL_FEEDBACK, "Подтянуть алгоритмы", null, null, List.of());
            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q1.getFeedback().getText()).isEqualTo("Хорошо");
            assertThat(q2.getFeedback().getScore()).isEqualTo(5);
            assertThat(session.getStatus()).isEqualTo(InterviewSession.Status.COMPLETED);
            assertThat(session.getCompletedAt()).isNotNull();
            assertThat(session.getReport()).isNotNull();
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.5);
            assertThat(session.getReport().getOfferProbability()).isEqualTo(InterviewReport.OfferProbability.HIGH);
            assertThat(session.getReport().getRecommendations()).isEqualTo("Подтянуть алгоритмы");
            verify(interviewSessionRepository).save(session);
        }

        @Test
        @DisplayName("Отвеченные вопросы содержат уточнения и неотвеченный основной - "
                + "removeIf убирает уточнения и неотвеченные из session.getQuestions(), "
                + "в отчёт и в avgScore идут только основные")
        void removesFollowUpsAndUnansweredQuestionsKeepingOnlyAnsweredMainsInReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion main1 = answeredMain(1);
            InterviewQuestion followUpOfMain1 = InterviewQuestion.builder()
                    .id(UUID.randomUUID()).parentQuestionId(main1.getId())
                    .text("Уточнение").answerText("Ответ на уточнение")
                    .orderIndex(1).followUp(true).answered(true).build();
            InterviewQuestion main2 = answeredMain(2);
            InterviewQuestion unansweredMain = InterviewQuestion.builder()
                    .id(UUID.randomUUID()).text("Неотвеченный вопрос")
                    .orderIndex(3).followUp(false).answered(false).build();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId)
                    .questions(new ArrayList<>(List.of(main1, followUpOfMain1, main2, unansweredMain))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4),
                            new LlmInterviewAnswerReview(2, "Отлично", 5)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            InterviewReportResponse expectedResponse = new InterviewReportResponse(
                    UUID.randomUUID(), sessionId, 4.5, InterviewReport.OfferProbability.MEDIUM,
                    OVERALL_FEEDBACK, null, null, null, List.of());
            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).containsExactly(main1, main2);
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.5);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<InterviewQuestion>> mainsCaptor = ArgumentCaptor.forClass(List.class);
            verify(interviewReportMapper).toResponse(any(InterviewReport.class), eq(session), mainsCaptor.capture());
            assertThat(mainsCaptor.getValue()).containsExactly(main1, main2);
        }

        @Test
        @DisplayName("Средний балл округляется до одного знака после запятой")
        void roundsAvgScoreToOneDecimalPlace() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewQuestion q3 = answeredMain(3);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2, q3))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // (3 + 4 + 4) / 3 = 3.6666... -> округление до 3.7
            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Норм", 3),
                            new LlmInterviewAnswerReview(2, "Хорошо", 4),
                            new LlmInterviewAnswerReview(3, "Хорошо", 4)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 3.7, InterviewReport.OfferProbability.MEDIUM,
                            OVERALL_FEEDBACK, null, null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(session.getReport().getAvgScore()).isEqualTo(3.7);
        }

        @Test
        @DisplayName("Review с индексом вне диапазона кейсов пропускается, средний балл считается только по валидным")
        void skipsOutOfRangeReviewButKeepsValidOnes() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4),
                            new LlmInterviewAnswerReview(99, "Вне диапазона", 5)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 4.0, InterviewReport.OfferProbability.MEDIUM,
                            OVERALL_FEEDBACK, null, null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Два review на один и тот же кейс - применяется только первый валидный, второй игнорируется как дубликат")
        void appliesOnlyFirstValidReviewPerCase() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Первый", 3),
                            new LlmInterviewAnswerReview(1, "Второй", 5)),
                    "Низкая", OVERALL_FEEDBACK, null, null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 3.0, InterviewReport.OfferProbability.LOW,
                            OVERALL_FEEDBACK, null, null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(3);
            assertThat(q1.getFeedback().getText()).isEqualTo("Первый");
        }

        @Test
        @DisplayName("Score вне диапазона 1-5 или пустая evaluation - review не применяется")
        void skipsReviewWithInvalidScoreOrBlankEvaluation() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Оценка вне диапазона", 6),
                            new LlmInterviewAnswerReview(2, "   ", 4)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has too few reviewed answers");
            assertThat(q1.getFeedback()).isNull();
            assertThat(q2.getFeedback()).isNull();
        }

        @Test
        @DisplayName("Score = null - review не применяется (без NPE)")
        void skipsReviewWithNullScore() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Без оценки", null),
                            new LlmInterviewAnswerReview(2, "Отлично", 5)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 5.0, InterviewReport.OfferProbability.MEDIUM,
                            OVERALL_FEEDBACK, null, null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback()).isNull();
            assertThat(q2.getFeedback().getScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("cases в ответе LLM - null (answers()==null), значит 0 из 1 кейса оценено - порог не пройден - LlmException")
        void throwsWhenTooFewReviewedAnswers() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(null, "Средняя", OVERALL_FEEDBACK, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has too few reviewed answers");
            verifyNoInteractions(interviewReportMapper);
        }

        @Test
        @DisplayName("Оценено ровно 50% кейсов (1 из 2) - порог строгий (reviewed < size*0.5) - отчёт успешно завершается")
        void completesWhenExactlyHalfOfCasesReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            InterviewReportResponse expectedResponse = new InterviewReportResponse(
                    UUID.randomUUID(), sessionId, 4.0, InterviewReport.OfferProbability.MEDIUM,
                    OVERALL_FEEDBACK, null, null, null, List.of());
            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getStatus()).isEqualTo(InterviewSession.Status.COMPLETED);
        }

        @Test
        @DisplayName("Оценено меньше 50% кейсов (1 из 3) - LlmException")
        void throwsWhenFewerThanHalfOfCasesReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewQuestion q2 = answeredMain(2);
            InterviewQuestion q3 = answeredMain(3);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1, q2, q3))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4)),
                    "Средняя", OVERALL_FEEDBACK, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has too few reviewed answers");
            verifyNoInteractions(interviewReportMapper);
        }

        @Test
        @DisplayName("Нет отвеченных вопросов вовсе - кейсов 0, порог формально пройден (0<0 = false), "
                + "но считать средний балл не по чему - LlmException")
        void throwsWhenNoAnsweredQuestionsAtAllLeavesNoScoresToAverage() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion unanswered = InterviewQuestion.builder()
                    .id(UUID.randomUUID()).text("Вопрос").orderIndex(1).followUp(false).answered(false).build();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(unanswered))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), "Средняя", OVERALL_FEEDBACK, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable scores");
            verifyNoInteractions(interviewReportMapper);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   ", "коротко"})
        @DisplayName("overallFeedback null, пробельный или короче 10 символов - LlmException")
        void throwsWhenOverallFeedbackNotUsable(String overallFeedback) {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>()).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), "Средняя", overallFeedback, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable overall feedback");
            verifyNoInteractions(interviewReportMapper);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "не число"})
        @DisplayName("offerProbability не парсится (null/пустая/невалидная строка) - LlmException")
        void throwsWhenOfferProbabilityInvalid(String offerProbability) {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>()).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), offerProbability, OVERALL_FEEDBACK, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable offer probability");
            verifyNoInteractions(interviewReportMapper);
        }

        @Test
        @DisplayName("recommendations пустая строка/пробелы - в сущности сохраняется null")
        void normalizesBlankRecommendationsToNull() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4)),
                    "Средняя", OVERALL_FEEDBACK, "   ", null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 4.0, InterviewReport.OfferProbability.MEDIUM,
                            OVERALL_FEEDBACK, null, null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(session.getReport().getRecommendations()).isNull();
        }

        @Test
        @DisplayName("recommendations непустая строка - сохраняется в сущности как есть")
        void keepsNonBlankRecommendationsAsIs() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewQuestion q1 = answeredMain(1);
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>(List.of(q1))).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хорошо", 4)),
                    "Средняя", OVERALL_FEEDBACK, "Подтянуть SQL", null);

            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(new InterviewReportResponse(
                            UUID.randomUUID(), sessionId, 4.0, InterviewReport.OfferProbability.MEDIUM,
                            OVERALL_FEEDBACK, "Подтянуть SQL", null, null, List.of()));

            // when
            interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(session.getReport().getRecommendations()).isEqualTo("Подтянуть SQL");
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(
                    sessionId, new LlmInterviewReport(List.of(), "Средняя", "фидбэк", null, null)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).status(InterviewSession.Status.COMPLETED).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(
                    sessionId, new LlmInterviewReport(List.of(), "Средняя", OVERALL_FEEDBACK, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }
    }
}
