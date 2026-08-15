package ru.workbit.training.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.SkillDictRepository;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.model.TrainingFeedback;
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
import ru.workbit.util.DictText;

import java.time.Instant;
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
    private static final String SKILL = "Spring Boot";

    @Mock
    TrainingSessionRepository trainingSessionRepository;
    @Mock
    TrainingQuestionRepository trainingQuestionRepository;
    @Mock
    ProfessionDictRepository professionDictRepository;
    @Mock
    SkillDictRepository skillDictRepository;
    @Mock
    QuotaService quotaService;
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
        @DisplayName("Апсертит и профессию, и навык с посчитанным ключом сравнения, возвращает оба id")
        void upsertsBothProfessionAndSkill() {
            // given
            UUID professionId = UUID.randomUUID();
            UUID skillId = UUID.randomUUID();
            when(professionDictRepository.upsertAndIncrementUsage(PROFESSION, DictText.matchKey(PROFESSION)))
                    .thenReturn(professionId);
            when(skillDictRepository.upsertAndIncrementUsage(professionId, SKILL, DictText.matchKey(SKILL)))
                    .thenReturn(skillId);

            // when
            TrainingWriter.DictionaryRefs result = trainingWriter.upsertDictionaries(SKILL, PROFESSION);

            // then
            assertThat(result.professionId()).isEqualTo(professionId);
            assertThat(result.skillId()).isEqualTo(skillId);
            verify(skillDictRepository).upsertAndIncrementUsage(professionId, SKILL, DictText.matchKey(SKILL));
        }
    }

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        @Test
        @DisplayName("Банковские вопросы идут первыми с bankQuestionId и скопированным referenceAnswer (blank -> null), "
                + "затем сгенерированные без bankQuestionId и без referenceAnswer; orderIndex 1..N по порядку")
        void ordersBankThenGeneratedQuestionsCopyingReferenceAnswer() {
            // given
            TrainingSession session = TrainingSession.builder().skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            UUID bankId1 = UUID.randomUUID();
            UUID bankId2 = UUID.randomUUID();
            BankQuestion bank1 = BankQuestion.builder().id(bankId1).text("Банковский вопрос 1")
                    .referenceAnswer("Эталонный ответ 1").build();
            BankQuestion bank2 = BankQuestion.builder().id(bankId2).text("Банковский вопрос 2")
                    .referenceAnswer("   ").build();
            List<String> generated = List.of("Сгенерированный вопрос");

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 3, null, null);
            when(trainingSessionMapper.toResponse(session, 0, 3)).thenReturn(expectedResponse);

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
            assertThat(first.getReferenceAnswer()).isEqualTo("Эталонный ответ 1");
            assertThat(first.getTrainingSession()).isSameAs(session);

            TrainingQuestion second = questions.get(1);
            assertThat(second.getBankQuestionId()).isEqualTo(bankId2);
            assertThat(second.getOrderIndex()).isEqualTo(2);
            assertThat(second.getReferenceAnswer()).isNull();

            TrainingQuestion third = questions.get(2);
            assertThat(third.getBankQuestionId()).isNull();
            assertThat(third.getOrderIndex()).isEqualTo(3);
            assertThat(third.getText()).isEqualTo("Сгенерированный вопрос");
            assertThat(third.getReferenceAnswer()).isNull();

            verify(trainingSessionRepository).save(session);
            verify(quotaService).debitTraining(session.getUserId(), "Тренировка — " + SKILL + ", "
                    + TrainingSession.Level.MIDDLE.getLabel());
        }

        @Test
        @DisplayName("Банк и генерация пусты - сохраняет сессию с пустым списком вопросов, answeredCount=0")
        void emptyQuestionsSavesSessionWithEmptyList() {
            // given
            TrainingSession session = TrainingSession.builder().skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE).build();
            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 0, null, null);
            when(trainingSessionMapper.toResponse(session, 0, 0)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingWriter.createSession(session, List.of(), List.of());

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).isEmpty();
            verify(trainingSessionRepository).save(session);
        }
    }

    @Nested
    @DisplayName("AppendQuestions")
    class AppendQuestions {

        @Test
        @DisplayName("Продолжает нумерацию с max(orderIndex)+1 - сначала банковские с bankQuestionId/referenceAnswer, "
                + "затем сгенерированные без них; answered/total считаются по итоговому списку")
        void continuesOrderIndexNumberingAppendingBankThenGenerated() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion existingAnswered = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Существующий вопрос 1").orderIndex(1).answered(true).build();
            TrainingQuestion existingUnanswered = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Существующий вопрос 2").orderIndex(2).answered(false).build();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.IN_PROGRESS)
                    .questions(new ArrayList<>(List.of(existingAnswered, existingUnanswered)))
                    .build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            UUID bankId = UUID.randomUUID();
            BankQuestion bankQuestion = BankQuestion.builder()
                    .id(bankId).text("Банковский вопрос").referenceAnswer("Эталон").build();
            List<String> generated = List.of("Сгенерированный вопрос");

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.IN_PROGRESS,
                    1, 4, null, null);
            when(trainingSessionMapper.toResponse(session, 1, 4)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingWriter.appendQuestions(sessionId, List.of(bankQuestion), generated);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            List<TrainingQuestion> questions = session.getQuestions();
            assertThat(questions).hasSize(4);

            TrainingQuestion appendedBank = questions.get(2);
            assertThat(appendedBank.getBankQuestionId()).isEqualTo(bankId);
            assertThat(appendedBank.getOrderIndex()).isEqualTo(3);
            assertThat(appendedBank.getReferenceAnswer()).isEqualTo("Эталон");

            TrainingQuestion appendedGenerated = questions.get(3);
            assertThat(appendedGenerated.getBankQuestionId()).isNull();
            assertThat(appendedGenerated.getOrderIndex()).isEqualTo(4);
            assertThat(appendedGenerated.getText()).isEqualTo("Сгенерированный вопрос");
            assertThat(appendedGenerated.getReferenceAnswer()).isNull();

            verify(trainingSessionRepository).save(session);
            verify(trainingSessionMapper).toResponse(session, 1, 4);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.appendQuestions(sessionId, List.of(), List.of()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verify(trainingSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED)
                    .questions(new ArrayList<>())
                    .build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingWriter.appendQuestions(sessionId, List.of(), List.of()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verify(trainingSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("RestartSession")
    class RestartSession {

        @Test
        @DisplayName("Сбрасывает feedback/answered/answerText/answeredAt у каждого вопроса, отчёт и статус сессии; "
                + "вопросы и эталонные ответы остаются на месте")
        void resetsAnswersFeedbackAndReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingFeedback feedback = TrainingFeedback.builder().score(4).text("Фидбэк").build();
            TrainingQuestion question = TrainingQuestion.builder()
                    .id(UUID.randomUUID())
                    .text("Вопрос")
                    .referenceAnswer("Эталонный ответ")
                    .orderIndex(1)
                    .answered(true)
                    .answerText("Ответ пользователя")
                    .answeredAt(Instant.now())
                    .feedback(feedback)
                    .build();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED)
                    .completedAt(Instant.now())
                    .report(TrainingReport.builder().avgScore(4.0).overallFeedback("Фидбэк").build())
                    .questions(new ArrayList<>(List.of(question)))
                    .build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 1, null, null);
            when(trainingSessionMapper.toResponse(session, 0, 1)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingWriter.restartSession(sessionId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(question.getFeedback()).isNull();
            assertThat(question.isAnswered()).isFalse();
            assertThat(question.getAnswerText()).isNull();
            assertThat(question.getAnsweredAt()).isNull();
            assertThat(question.getText()).isEqualTo("Вопрос");
            assertThat(question.getReferenceAnswer()).isEqualTo("Эталонный ответ");
            assertThat(session.getReport()).isNull();
            assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.CREATED);
            assertThat(session.getCompletedAt()).isNull();
            verify(trainingSessionRepository).save(session);
            verify(quotaService).debitTraining(session.getUserId(), "Тренировка — " + SKILL + ", "
                    + TrainingSession.Level.MIDDLE.getLabel());
        }

        @Test
        @DisplayName("Списывает тренировку с label «Тренировка — {навык}, {лейбл уровня}»")
        void debitsTrainingWithFormattedLabel() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill("Java").profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED)
                    .questions(new ArrayList<>())
                    .build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            when(trainingSessionMapper.toResponse(session, 0, 0)).thenReturn(
                    new TrainingSessionResponse(sessionId, "Java", PROFESSION, TrainingSession.Level.MIDDLE,
                            TrainingSession.Status.CREATED, 0, 0, null, null));

            // when
            trainingWriter.restartSession(sessionId);

            // then
            verify(quotaService).debitTraining(session.getUserId(), "Тренировка — Java, Уверенный");
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.restartSession(sessionId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verify(trainingSessionRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = TrainingSession.Status.class, names = {"CREATED", "IN_PROGRESS"})
        @DisplayName("Сессия ещё не завершена - ConflictException, сброс не происходит")
        void throwsWhenSessionNotCompleted(TrainingSession.Status status) {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .status(status)
                    .questions(new ArrayList<>())
                    .build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingWriter.restartSession(sessionId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session is not finished");
            verify(trainingSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("SaveReferenceAnswer")
    class SaveReferenceAnswer {

        @Test
        @DisplayName("Вопрос найден - проставляет эталонный ответ (dirty checking, без явного save)")
        void setsReferenceAnswerOnFoundQuestion() {
            // given
            UUID questionId = UUID.randomUUID();
            TrainingQuestion question = TrainingQuestion.builder().id(questionId).build();
            when(trainingQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));

            // when
            trainingWriter.saveReferenceAnswer(questionId, "Новый эталонный ответ");

            // then
            assertThat(question.getReferenceAnswer()).isEqualTo("Новый эталонный ответ");
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            UUID questionId = UUID.randomUUID();
            when(trainingQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingWriter.saveReferenceAnswer(questionId, "Ответ"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
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
                    .answered(true)
                    .build();
        }

        @Test
        @DisplayName("Валидный отчёт - проставляет фидбэки по вопросам, средний балл, переводит сессию в COMPLETED")
        void completesSessionWithValidReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(2, "Отлично", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.5,
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
        @DisplayName("Среди вопросов сессии есть неотвеченный - removeIf убирает его из session.getQuestions(), "
                + "в отчёт и в avgScore идут только отвеченные")
        void removesUnansweredQuestionsKeepingOnlyAnsweredOnesInReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingQuestion unanswered = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Неотвеченный вопрос")
                    .orderIndex(3).answered(false).build();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, unanswered, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(2, "Отлично", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.5,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            TrainingReportResponse result = trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).containsExactly(q1, q2);
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.5);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TrainingQuestion>> answeredCaptor = ArgumentCaptor.forClass(List.class);
            verify(trainingReportMapper).toResponse(any(TrainingReport.class), eq(session), answeredCaptor.capture());
            assertThat(answeredCaptor.getValue()).containsExactly(q1, q2);
        }

        @Test
        @DisplayName("Review с индексом вне диапазона вопросов пропускается, средний балл считается только по валидным")
        void skipsOutOfRangeReviewButKeepsValidOnes() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4),
                            new LlmTrainingCaseReview(99, "Вне диапазона", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(new TrainingReportResponse(
                            UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.0,
                            llmReport.overallFeedback(), null, List.of()));

            // when
            trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(4);
            assertThat(q2.getFeedback()).isNull();
            assertThat(session.getReport().getAvgScore()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Два review на один и тот же вопрос - применяется только первый валидный, второй игнорируется")
        void appliesOnlyFirstValidReviewPerQuestion() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Первый", 3),
                            new LlmTrainingCaseReview(1, "Второй", 5)),
                    "Общий развёрнутый фидбэк по тренировке");

            when(trainingReportMapper.toResponse(any(TrainingReport.class), eq(session), any()))
                    .thenReturn(new TrainingReportResponse(
                            UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 3.0,
                            llmReport.overallFeedback(), null, List.of()));

            // when
            trainingWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(q1.getFeedback().getScore()).isEqualTo(3);
            assertThat(q1.getFeedback().getText()).isEqualTo("Первый");
        }

        @Test
        @DisplayName("Отвеченных вопросов нет (пустой список) - avgScore считать не по чему - LlmException")
        void throwsWhenNoAnsweredQuestionsToScore() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>()).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(List.of(), "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has no usable scores");
            verifyNoInteractions(trainingReportMapper);
        }

        @Test
        @DisplayName("cases в ответе LLM - null, значит 0 из 1 вопроса оценено - порог не пройден - LlmException")
        void throwsWhenTooFewReviewedQuestions() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(null, "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed questions");
            verifyNoInteractions(trainingReportMapper);
        }

        @Test
        @DisplayName("Оценено ровно 50% вопросов (1 из 2) - порог строгий (reviewed < size*0.5) - отчёт успешно завершается")
        void completesWhenExactlyHalfOfQuestionsReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.0,
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
        @DisplayName("Оценено меньше 50% вопросов (1 из 3) - LlmException")
        void throwsWhenFewerThanHalfOfQuestionsReviewed() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingQuestion q1 = answeredQuestion(1);
            TrainingQuestion q2 = answeredQuestion(2);
            TrainingQuestion q3 = answeredQuestion(3);
            TrainingSession session = TrainingSession.builder()
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2, q3))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Хорошо", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed questions");
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
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
                    .questions(new ArrayList<>(List.of(q1, q2))).build();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "Оценка вне диапазона", 6),
                            new LlmTrainingCaseReview(2, "   ", 4)),
                    "Общий развёрнутый фидбэк по тренировке");

            // when / then
            assertThatThrownBy(() -> trainingWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training report has too few reviewed questions");
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
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
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
                    .id(sessionId).skill(SKILL).profession(PROFESSION).level(TrainingSession.Level.MIDDLE)
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
