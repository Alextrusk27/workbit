package ru.workbit.training.service;

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
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.TopicDictRepository;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.mapper.TrainingQuestionMapper;
import ru.workbit.training.model.mapper.TrainingReportMapper;
import ru.workbit.training.model.mapper.TrainingSessionMapper;
import ru.workbit.training.repository.TrainingQuestionRepository;
import ru.workbit.training.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReport;

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
@DisplayName("TrainingWriterTest")
class TrainingWriterTest {

    private static final String PROFESSION = "Java-разработчик";
    private static final String TOPIC = "Spring Boot";

    @Mock
    TrainingSessionRepository trainingSessionRepository;
    @Mock
    TrainingQuestionRepository trainingQuestionRepository;
    @Mock
    ProfessionDictRepository professionDictRepository;
    @Mock
    TopicDictRepository topicDictRepository;
    @Mock
    TrainingSessionMapper trainingSessionMapper;
    @Mock
    TrainingQuestionMapper trainingQuestionMapper;
    @Mock
    TrainingReportMapper trainingReportMapper;

    @InjectMocks
    TrainingWriter trainingWriter;

    @Nested
    @DisplayName("UpsertDictionaries")
    class UpsertDictionaries {

        @Test
        @DisplayName("С темой - апсертит и профессию, и тему, возвращает оба id")
        void withTopicUpsertsBothProfessionAndTopic() {
            // given
            UUID professionId = UUID.randomUUID();
            UUID topicId = UUID.randomUUID();
            when(professionDictRepository.upsertAndIncrementUsage(PROFESSION)).thenReturn(professionId);
            when(topicDictRepository.upsertAndIncrementUsage(professionId, TOPIC)).thenReturn(topicId);

            // when
            TrainingWriter.DictionaryRefs result = trainingWriter.upsertDictionaries(PROFESSION, TOPIC);

            // then
            assertThat(result.professionId()).isEqualTo(professionId);
            assertThat(result.topicId()).isEqualTo(topicId);
            verify(topicDictRepository).upsertAndIncrementUsage(professionId, TOPIC);
        }

        @Test
        @DisplayName("Без темы (null) - тема не апсертится, topicId в результате null")
        void withoutTopicSkipsTopicUpsert() {
            // given
            UUID professionId = UUID.randomUUID();
            when(professionDictRepository.upsertAndIncrementUsage(PROFESSION)).thenReturn(professionId);

            // when
            TrainingWriter.DictionaryRefs result = trainingWriter.upsertDictionaries(PROFESSION, null);

            // then
            assertThat(result.professionId()).isEqualTo(professionId);
            assertThat(result.topicId()).isNull();
            verifyNoInteractions(topicDictRepository);
        }
    }

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Банковские вопросы идут первыми с bankQuestionId, затем сгенерированные без него; orderIndex 1..N по порядку, followUp=false")
        void ordersBankThenGeneratedQuestions() {
            // given
            TrainingSession session = TrainingSession.builder().profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            UUID bankId1 = UUID.randomUUID();
            UUID bankId2 = UUID.randomUUID();
            BankQuestion bank1 = BankQuestion.builder().id(bankId1).text("Банковский вопрос 1").build();
            BankQuestion bank2 = BankQuestion.builder().id(bankId2).text("Банковский вопрос 2").build();
            List<String> generated = List.of("Сгенерированный вопрос");

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, null, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(session, 0)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingWriter.createSession(session, List.of(bank1, bank2), generated);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            List<TrainingQuestion> questions = session.getQuestions();
            assertThat(questions).hasSize(3);

            TrainingQuestion first = questions.get(0);
            assertThat(first.getBankQuestionId()).isEqualTo(bankId1);
            assertThat(first.getOrderIndex()).isEqualTo(1);
            assertThat(first.getText()).isEqualTo("Банковский вопрос 1");
            assertThat(first.isFollowUp()).isFalse();
            assertThat(first.getTrainingSession()).isSameAs(session);

            TrainingQuestion second = questions.get(1);
            assertThat(second.getBankQuestionId()).isEqualTo(bankId2);
            assertThat(second.getOrderIndex()).isEqualTo(2);

            TrainingQuestion third = questions.get(2);
            assertThat(third.getBankQuestionId()).isNull();
            assertThat(third.getOrderIndex()).isEqualTo(3);
            assertThat(third.getText()).isEqualTo("Сгенерированный вопрос");
            assertThat(third.isFollowUp()).isFalse();

            verify(trainingSessionRepository).save(session);
        }

        @Test
        @DisplayName("Банк и генерация пусты - сохраняет сессию с пустым списком вопросов, answeredCount=0")
        void emptyQuestionsSavesSessionWithEmptyList() {
            // given
            TrainingSession session = TrainingSession.builder().profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, null, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(session, 0)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingWriter.createSession(session, List.of(), List.of());

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).isEmpty();
            verify(trainingSessionRepository).save(session);
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
            TrainingQuestion question = TrainingQuestion.builder().id(questionId).followUpChecked(false).build();
            when(trainingQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));

            // when
            trainingWriter.markFollowUpChecked(questionId);

            // then
            assertThat(question.isFollowUpChecked()).isTrue();
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            UUID questionId = UUID.randomUUID();
            when(trainingQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.markFollowUpChecked(questionId))
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
            TrainingSession session = TrainingSession.builder()
                    .id(UUID.randomUUID()).profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            TrainingQuestion answered = TrainingQuestion.builder()
                    .id(answeredId).trainingSession(session).text("Вопрос").orderIndex(1)
                    .followUpChecked(false).build();
            when(trainingQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(session.getId())).thenReturn(Optional.empty());
            when(trainingQuestionRepository.countByParentQuestionId(caseMainId)).thenReturn(2L);
            when(trainingQuestionRepository.save(any(TrainingQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    UUID.randomUUID(), 3, "Новое уточнение", true, null, null, null);
            when(trainingQuestionMapper.toDto(any(TrainingQuestion.class))).thenReturn(expectedResponse);

            // when
            TrainingQuestionResponse result = trainingWriter.saveFollowUp(answeredId, caseMainId, "Новое уточнение");

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(answered.isFollowUpChecked()).isTrue();

            ArgumentCaptor<TrainingQuestion> captor = ArgumentCaptor.forClass(TrainingQuestion.class);
            verify(trainingQuestionRepository).save(captor.capture());
            TrainingQuestion saved = captor.getValue();
            assertThat(saved.getOrderIndex()).isEqualTo(3);
            assertThat(saved.getParentQuestionId()).isEqualTo(caseMainId);
            assertThat(saved.getText()).isEqualTo("Новое уточнение");
            assertThat(saved.isFollowUp()).isTrue();
            assertThat(saved.getTrainingSession()).isSameAs(session);
        }

        @Test
        @DisplayName("Гонка - findNextUnansweredFollowUp уже нашёл ожидающий follow-up - возвращает его, новый не сохраняется")
        void returnsExistingPendingFollowUpInsteadOfSavingNew() {
            // given
            UUID answeredId = UUID.randomUUID();
            UUID caseMainId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(UUID.randomUUID()).profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            TrainingQuestion answered = TrainingQuestion.builder()
                    .id(answeredId).trainingSession(session).text("Вопрос").orderIndex(1)
                    .followUpChecked(false).build();
            when(trainingQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            TrainingQuestion pendingFollowUp = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Уже создано").orderIndex(1).followUp(true).build();
            when(trainingQuestionRepository.findNextUnansweredFollowUp(session.getId()))
                    .thenReturn(Optional.of(pendingFollowUp));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    pendingFollowUp.getId(), 1, "Уже создано", true, null, null, null);
            when(trainingQuestionMapper.toDto(pendingFollowUp)).thenReturn(expectedResponse);

            // when
            TrainingQuestionResponse result = trainingWriter.saveFollowUp(answeredId, caseMainId, "Новый вопрос");

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(answered.isFollowUpChecked()).isTrue();
            verify(trainingQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Отвеченный вопрос не найден - NotFoundException")
        void throwsWhenAnsweredQuestionNotFound() {
            // given
            UUID answeredId = UUID.randomUUID();
            when(trainingQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.saveFollowUp(answeredId, UUID.randomUUID(), "Уточнение"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID answeredId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(UUID.randomUUID()).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED).build();
            TrainingQuestion answered = TrainingQuestion.builder()
                    .id(answeredId).trainingSession(session).text("Вопрос").orderIndex(1).build();
            when(trainingQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            // when / then
            assertThatThrownBy(() -> trainingWriter.saveFollowUp(answeredId, UUID.randomUUID(), "Уточнение"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verify(trainingQuestionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("CompleteReport")
    class CompleteReport {

        private TrainingQuestion answeredQuestion(int orderIndex) {
            return TrainingQuestion.builder()
                    .id(UUID.randomUUID())
                    .text("Вопрос " + orderIndex)
                    .answerText("Ответ " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(false)
                    .answered(true)
                    .build();
        }

        @Test
        @DisplayName("Валидный отчёт - проставляет фидбэки по кейсам, средний балл, переводит сессию в COMPLETED")
        void completesSessionWithValidReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(2, "Отлично", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, PROFESSION, null, TrainingSession.Level.MIDDLE, 4.5,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            TrainingReportResponse result = trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q1.getFeedback().getText()).isEqualTo("Хорошо");
            assertThat(q2.getFeedback().getScore()).isEqualTo(5);
            assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.COMPLETED);
            assertThat(session.getCompletedAt()).isNotNull();
            assertThat(session.getReport()).isNotNull();
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.5);
            verify(trainingSessionRepository).save(session);
        }

        @Test
        @DisplayName("Отвеченные вопросы содержат уточнения и неотвеченный основной - "
                + "removeIf убирает уточнения и неотвеченные из session.getQuestions(), "
                + "в отчёт и в avgScore идут только основные")
        void removesFollowUpsAndUnansweredQuestionsKeepingOnlyAnsweredMainsInReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion main1 = answeredQuestion(1);
            TrainingQuestion followUpOfMain1 = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).parentQuestionId(main1.getId())
                    .text("Уточнение").answerText("Ответ на уточнение")
                    .orderIndex(1).followUp(true).answered(true).build();
            TrainingQuestion main2 = answeredQuestion(2);
            TrainingQuestion unansweredMain = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Неотвеченный вопрос")
                    .orderIndex(3).followUp(false).answered(false).build();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(main1, followUpOfMain1, main2, unansweredMain))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(2, "Отлично", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, PROFESSION, null, TrainingSession.Level.MIDDLE, 4.5,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            TrainingReportResponse result = trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).containsExactly(main1, main2);
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.5);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TrainingQuestion>> mainsCaptor = ArgumentCaptor.forClass(List.class);
            verify(trainingReportMapper).toResponse(any(TrainingReport.class), eq(session), mainsCaptor.capture());
            assertThat(mainsCaptor.getValue()).containsExactly(main1, main2);
        }

        @Test
        @DisplayName("Review с индексом вне диапазона кейсов пропускается, средний балл считается только по валидным")
        void skipsOutOfRangeReviewButKeepsValidOnes() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(99, "Вне диапазона", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(new TrainingReportResponse(
                            UUID.randomUUID(), sessionId, PROFESSION, null, TrainingSession.Level.MIDDLE, 4.0,
                            llmReport.overallFeedback(), null, List.of()));

            // when
            trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Два review на один и тот же кейс - применяется только первый валидный, второй игнорируется")
        void appliesOnlyFirstValidReviewPerCase() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Первый", 3),
                            new LlmTrainingCaseReview(1, "Второй", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(new TrainingReportResponse(
                            UUID.randomUUID(), sessionId, PROFESSION, null, TrainingSession.Level.MIDDLE, 3.0,
                            llmReport.overallFeedback(), null, List.of()));

            // when
            trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(3);
            assertThat(q1.getFeedback().getText()).isEqualTo("Первый");
        }

        @Test
        @DisplayName("cases в ответе LLM - null, значит 0 из 1 кейса оценено - порог не пройден - LlmException")
        void throwsWhenTooFewReviewedCases() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(null, "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed cases");
            verifyNoInteractions(trainingReportMapper);
        }

        @Test
        @DisplayName("Оценено ровно 50% кейсов (1 из 2) - порог строгий (reviewed < size*0.5) - отчёт успешно завершается")
        void completesWhenExactlyHalfOfCasesReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, PROFESSION, null, TrainingSession.Level.MIDDLE, 4.0,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            TrainingReportResponse result = trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.COMPLETED);
        }

        @Test
        @DisplayName("Оценено меньше 50% кейсов (1 из 3) - LlmException")
        void throwsWhenFewerThanHalfOfCasesReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingQuestion q3 = answeredQuestion(3);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2, q3))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed cases");
            verifyNoInteractions(trainingReportMapper);
        }

        @Test
        @DisplayName("Score вне диапазона 1-5 или пустая evaluation - review не применяется")
        void skipsReviewWithInvalidScoreOrBlankEvaluation() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Оценка вне диапазона", 6),
                            new LlmTrainingCaseReview(2, "   ", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed cases");
            assertThat(q1.getFeedback()).isNull();
            assertThat(q2.getFeedback()).isNull();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   ", "коротко"})
        @DisplayName("overallFeedback null, пробельный или короче 10 символов - LlmException")
        void throwsWhenOverallFeedbackNotUsable(String overallFeedback) {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>()).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(List.of(), overallFeedback);

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has no usable overall feedback");
            verifyNoInteractions(trainingReportMapper);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, new LlmTrainingReport(List.of(), "фидбэк")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(
                    sessionId, new LlmTrainingReport(List.of(), "Общий развёрнутый фидбэк по тренировке")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }
    }

}
