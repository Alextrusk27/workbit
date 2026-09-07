package ru.workbit.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTopicKind;
import ru.workbit.llm.dto.LlmOfferProbability;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.service.VacancyService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    @Spy
    ObjectMapper objectMapper = new JsonMapper();

    @InjectMocks
    InterviewWriter interviewWriter;

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        private static final LlmInterviewPlan PLAN = new LlmInterviewPlan(
                7,
                List.of(new LlmInterviewTopic("Java core", 4, LlmInterviewTopicKind.CORE),
                        new LlmInterviewTopic("Spring", 3, LlmInterviewTopicKind.STANDARD)),
                "Java core", "Что такое JVM?");

        @Test
        @DisplayName("Сохраняет снапшот вакансии, сессию из плана и единственный первый вопрос с kind MAIN")
        void savesSnapshotSessionAndFirstQuestion() {
            // given
            UUID userId = UUID.randomUUID();
            UUID vacancySnapshotId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(vacancySnapshotId);
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            InterviewSession result = interviewWriter.createSession(VACANCY_DATA, userId, PLAN);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getVacancySnapshotId()).isEqualTo(vacancySnapshotId);
            assertThat(result.getTotalQuestions()).isEqualTo(7);
            assertThat(result.getPlanTopics()).isEqualTo(
                    "[{\"name\":\"Java core\",\"questions\":4,\"kind\":\"CORE\"},"
                            + "{\"name\":\"Spring\",\"questions\":3,\"kind\":\"STANDARD\"}]");

            assertThat(result.getQuestions()).hasSize(1);
            InterviewQuestion first = result.getQuestions().getFirst();
            assertThat(first.getText()).isEqualTo("Что такое JVM?");
            assertThat(first.getTopic()).isEqualTo("Java core");
            assertThat(first.getKind()).isEqualTo(InterviewQuestion.Kind.MAIN);
            assertThat(first.getOrderIndex()).isEqualTo(1);
            assertThat(first.isFollowUp()).isFalse();
            assertThat(first.getSession()).isSameAs(result);

            verify(interviewSessionRepository).save(result);
        }

        @Test
        @DisplayName("Списывает интервью с label «Интервью — {название вакансии}» до сохранения снапшота и сессии")
        void debitsInterviewBeforeSaving() {
            // given
            UUID userId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(UUID.randomUUID());
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            interviewWriter.createSession(VACANCY_DATA, userId, PLAN);

            // then
            InOrder order = inOrder(quotaService, vacancyService, interviewSessionRepository);
            order.verify(quotaService).debitInterview(userId, "Интервью — Java-разработчик");
            order.verify(vacancyService).saveSnapshot(VACANCY_DATA);
            order.verify(interviewSessionRepository).save(any(InterviewSession.class));
        }

        @Test
        @DisplayName("План без тем - сессия создаётся, planTopics остаётся null")
        void savesSessionWithoutPlanTopics() {
            // given
            UUID userId = UUID.randomUUID();
            when(vacancyService.saveSnapshot(VACANCY_DATA)).thenReturn(UUID.randomUUID());
            when(interviewSessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            InterviewSession result = interviewWriter.createSession(VACANCY_DATA, userId,
                    new LlmInterviewPlan(5, null, null, "Первый вопрос"));

            // then
            assertThat(result.getPlanTopics()).isNull();
            assertThat(result.getTotalQuestions()).isEqualTo(5);
            assertThat(result.getQuestions().getFirst().getTopic()).isNull();
        }
    }

    @Nested
    @DisplayName("SaveStep")
    class SaveStep {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID answeredId = UUID.randomUUID();

        private InterviewSession activeSession() {
            return InterviewSession.builder().id(sessionId).totalQuestions(5).build();
        }

        private InterviewQuestion answeredMain(InterviewSession session) {
            return InterviewQuestion.builder()
                    .id(answeredId).session(session).text("Основной вопрос").orderIndex(2)
                    .kind(InterviewQuestion.Kind.MAIN).answered(true).followUpChecked(false).build();
        }

        @Test
        @DisplayName("kind MAIN - новый основной вопрос без родителя, orderIndex по числу основных, "
                + "отвеченный помечается проверенным")
        void savesMainQuestion() {
            // given
            InterviewSession session = activeSession();
            InterviewQuestion answered = answeredMain(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndKind(sessionId, InterviewQuestion.Kind.MAIN))
                    .thenReturn(2L);
            when(interviewQuestionRepository.save(any(InterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

            InterviewQuestionResponse expectedResponse = new InterviewQuestionResponse(
                    UUID.randomUUID(), 3, "Следующий основной", false, null, null, null);
            when(interviewQuestionMapper.toDto(any(InterviewQuestion.class))).thenReturn(expectedResponse);

            // when
            Optional<InterviewQuestionResponse> result = interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.MAIN, "Следующий основной", "Spring");

            // then
            assertThat(result).contains(expectedResponse);
            assertThat(answered.isFollowUpChecked()).isTrue();

            ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(interviewQuestionRepository).save(captor.capture());
            InterviewQuestion saved = captor.getValue();
            assertThat(saved.getParentQuestionId()).isNull();
            assertThat(saved.getKind()).isEqualTo(InterviewQuestion.Kind.MAIN);
            assertThat(saved.getTopic()).isEqualTo("Spring");
            assertThat(saved.getOrderIndex()).isEqualTo(3);
            assertThat(saved.isFollowUp()).isFalse();
            assertThat(saved.getSession()).isSameAs(session);
        }

        @Test
        @DisplayName("Ответ дан на основной вопрос - уточнение становится его ребёнком, orderIndex по числу детей кейса")
        void savesChildOfAnsweredMain() {
            // given
            InterviewSession session = activeSession();
            InterviewQuestion answered = answeredMain(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answeredId))
                    .thenReturn(List.of(InterviewQuestion.builder()
                            .id(UUID.randomUUID()).parentQuestionId(answeredId).text("Переспрос")
                            .kind(InterviewQuestion.Kind.CLARIFICATION).orderIndex(1).followUp(true).build()));
            when(interviewQuestionRepository.save(any(InterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

            InterviewQuestionResponse expectedResponse = new InterviewQuestionResponse(
                    UUID.randomUUID(), 2, "А как это работает под нагрузкой?", true, null, null, null);
            when(interviewQuestionMapper.toDto(any(InterviewQuestion.class))).thenReturn(expectedResponse);

            // when
            Optional<InterviewQuestionResponse> result = interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.FOLLOW_UP, "А как это работает под нагрузкой?", "Spring");

            // then
            assertThat(result).contains(expectedResponse);

            ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(interviewQuestionRepository).save(captor.capture());
            InterviewQuestion saved = captor.getValue();
            assertThat(saved.getParentQuestionId()).isEqualTo(answeredId);
            assertThat(saved.getKind()).isEqualTo(InterviewQuestion.Kind.FOLLOW_UP);
            assertThat(saved.getOrderIndex()).isEqualTo(2);
            assertThat(saved.isFollowUp()).isTrue();
            verify(interviewQuestionRepository, never()).countBySessionIdAndKind(any(), any());
        }

        @Test
        @DisplayName("Ответ дан на уточнение - новый ребёнок вешается на тот же основной вопрос, а не на уточнение")
        void savesChildOfSameCaseWhenAnsweredIsChild() {
            // given
            UUID mainId = UUID.randomUUID();
            InterviewSession session = activeSession();
            InterviewQuestion answeredChild = InterviewQuestion.builder()
                    .id(answeredId).session(session).parentQuestionId(mainId).text("Переспрос")
                    .kind(InterviewQuestion.Kind.CLARIFICATION).orderIndex(1).followUp(true)
                    .answered(true).followUpChecked(false).build();
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answeredChild));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(mainId))
                    .thenReturn(List.of(answeredChild));
            when(interviewQuestionRepository.save(any(InterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(interviewQuestionMapper.toDto(any(InterviewQuestion.class))).thenReturn(mock(InterviewQuestionResponse.class));

            // when
            interviewWriter.saveStep(answeredId, InterviewQuestion.Kind.REDIRECT, "Вернёмся к вопросу", "Spring");

            // then
            ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(interviewQuestionRepository).save(captor.capture());
            InterviewQuestion saved = captor.getValue();
            assertThat(saved.getParentQuestionId()).isEqualTo(mainId);
            assertThat(saved.getKind()).isEqualTo(InterviewQuestion.Kind.REDIRECT);
            assertThat(saved.getOrderIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("По ответу уже сходил параллельный запрос - возвращается созданный им вопрос, новый не сохраняется")
        void returnsQuestionOfParallelRequestInsteadOfSavingNew() {
            // given
            InterviewSession session = activeSession();
            InterviewQuestion answered = answeredMain(session);
            answered.setFollowUpChecked(true);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            InterviewQuestion pending = InterviewQuestion.builder()
                    .id(UUID.randomUUID()).parentQuestionId(answeredId).text("Уже задано")
                    .kind(InterviewQuestion.Kind.FOLLOW_UP).orderIndex(1).followUp(true).answered(false).build();
            when(interviewQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.of(pending));

            InterviewQuestionResponse expectedResponse = new InterviewQuestionResponse(
                    pending.getId(), 1, "Уже задано", true, null, null, null);
            when(interviewQuestionMapper.toDto(pending)).thenReturn(expectedResponse);

            // when
            Optional<InterviewQuestionResponse> result = interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.FOLLOW_UP, "Новое уточнение", "Spring");

            // then
            assertThat(result).contains(expectedResponse);
            verify(interviewQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("По ответу уже сходил параллельный запрос, но неотвеченного вопроса нет - пусто, новый не сохраняется")
        void returnsEmptyWhenAlreadyCheckedAndNothingPending() {
            // given
            InterviewSession session = activeSession();
            InterviewQuestion answered = answeredMain(session);
            answered.setFollowUpChecked(true);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.empty());

            // when
            Optional<InterviewQuestionResponse> result = interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.MAIN, "Следующий основной", "Spring");

            // then
            assertThat(result).isEmpty();
            verify(interviewQuestionRepository, never()).save(any());
            verifyNoInteractions(interviewQuestionMapper);
        }

        @Test
        @DisplayName("Отвеченный вопрос не найден - NotFoundException")
        void throwsWhenAnsweredQuestionNotFound() {
            // given
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.MAIN, "Вопрос", "Тема"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verify(interviewQuestionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException, вопрос не сохраняется")
        void throwsWhenSessionCompleted() {
            // given
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).status(InterviewSession.Status.COMPLETED).build();
            InterviewQuestion answered = answeredMain(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));

            // when / then
            assertThatThrownBy(() -> interviewWriter.saveStep(
                    answeredId, InterviewQuestion.Kind.MAIN, "Вопрос", "Тема"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verify(interviewQuestionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("CloseQuestioning")
    class CloseQuestioning {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID answeredId = UUID.randomUUID();

        private InterviewQuestion answeredIn(InterviewSession session) {
            return InterviewQuestion.builder()
                    .id(answeredId).session(session).text("Вопрос").orderIndex(3)
                    .kind(InterviewQuestion.Kind.MAIN).answered(true).followUpChecked(false).build();
        }

        @Test
        @DisplayName("Досрочный конец беседы - помечает ответ проверенным и подрезает totalQuestions до отвеченных основных")
        void trimsTotalQuestionsOnEarlyFinish() {
            // given
            InterviewSession session = InterviewSession.builder().id(sessionId).totalQuestions(8).build();
            InterviewQuestion answered = answeredIn(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(3L);

            // when
            interviewWriter.closeQuestioning(answeredId, null);

            // then
            assertThat(answered.isFollowUpChecked()).isTrue();
            assertThat(session.getTotalQuestions()).isEqualTo(3);
        }

        @Test
        @DisplayName("Отвечены все основные вопросы плана - totalQuestions не меняется")
        void keepsTotalQuestionsWhenAllMainAnswered() {
            // given
            InterviewSession session = InterviewSession.builder().id(sessionId).totalQuestions(5).build();
            InterviewQuestion answered = answeredIn(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(5L);

            // when
            interviewWriter.closeQuestioning(answeredId, null);

            // then
            assertThat(answered.isFollowUpChecked()).isTrue();
            assertThat(session.getTotalQuestions()).isEqualTo(5);
        }

        @Test
        @DisplayName("Ни одного отвеченного основного - totalQuestions не обнуляется")
        void keepsTotalQuestionsWhenNoMainAnswered() {
            // given
            InterviewSession session = InterviewSession.builder().id(sessionId).totalQuestions(5).build();
            InterviewQuestion answered = answeredIn(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(0L);

            // when
            interviewWriter.closeQuestioning(answeredId, null);

            // then
            assertThat(session.getTotalQuestions()).isEqualTo(5);
        }

        @Test
        @DisplayName("Интервьюер оборвал беседу - прощальная реплика сохраняется без крайних пробелов")
        void savesClosingRemarkTrimmed() {
            // given
            InterviewSession session = InterviewSession.builder().id(sessionId).totalQuestions(5).build();
            InterviewQuestion answered = answeredIn(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(3L);

            // when
            interviewWriter.closeQuestioning(answeredId, "  Давайте на этом остановимся.  ");

            // then
            assertThat(session.getClosingRemark()).isEqualTo("Давайте на этом остановимся.");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   "})
        @DisplayName("Прощальной реплики нет - closingRemark остаётся пустым")
        void keepsClosingRemarkEmptyWhenRemarkIsBlank(String closingRemark) {
            // given
            InterviewSession session = InterviewSession.builder().id(sessionId).totalQuestions(5).build();
            InterviewQuestion answered = answeredIn(session);
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(3L);

            // when
            interviewWriter.closeQuestioning(answeredId, closingRemark);

            // then
            assertThat(session.getClosingRemark()).isNull();
        }

        @Test
        @DisplayName("Отвеченный вопрос не найден - NotFoundException")
        void throwsWhenAnsweredQuestionNotFound() {
            // given
            when(interviewQuestionRepository.findWithSessionById(answeredId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewWriter.closeQuestioning(answeredId, null))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
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
                    LlmOfferProbability.HIGH, OVERALL_FEEDBACK, "Подтянуть алгоритмы", null);

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
                + "removeIf убирает из session.getQuestions() только неотвеченные, отвеченное "
                + "уточнение остаётся в БД, а в отчёт и в avgScore идут лишь основные")
        void removesUnansweredQuestionsKeepingAnsweredFollowUpsOutOfReport() {
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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

            InterviewReportResponse expectedResponse = new InterviewReportResponse(
                    UUID.randomUUID(), sessionId, 4.5, InterviewReport.OfferProbability.MEDIUM,
                    OVERALL_FEEDBACK, null, null, null, List.of());
            when(interviewReportMapper.toResponse(any(InterviewReport.class), eq(session), any()))
                    .thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewWriter.completeReport(sessionId, llmReport);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getQuestions()).containsExactly(main1, followUpOfMain1, main2);
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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.LOW, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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

            LlmInterviewReport llmReport = new LlmInterviewReport(null, LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null);

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

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), LlmOfferProbability.MEDIUM, overallFeedback, null, null);

            // when / then
            assertThatThrownBy(() -> interviewWriter.completeReport(sessionId, llmReport))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview report has no usable overall feedback");
            verifyNoInteractions(interviewReportMapper);
        }

        @Test
        @DisplayName("offerProbability не пришёл - LlmException")
        void throwsWhenOfferProbabilityMissing() {
            // given
            UUID sessionId = UUID.randomUUID();
            InterviewSession session = InterviewSession.builder()
                    .id(sessionId).questions(new ArrayList<>()).build();
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmInterviewReport llmReport = new LlmInterviewReport(List.of(), null, OVERALL_FEEDBACK, null, null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, "   ", null);

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
                    LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, "Подтянуть SQL", null);

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
                    sessionId, new LlmInterviewReport(List.of(), LlmOfferProbability.MEDIUM, "фидбэк", null, null)))
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
                    sessionId, new LlmInterviewReport(List.of(), LlmOfferProbability.MEDIUM, OVERALL_FEEDBACK, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }
    }
}
