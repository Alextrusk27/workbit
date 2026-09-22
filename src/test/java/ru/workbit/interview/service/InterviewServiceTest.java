package ru.workbit.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.workbit.billing.model.UsageEvent;
import ru.workbit.billing.service.LimitService;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.PaymentRequiredException;
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
import ru.workbit.llm.dto.LlmInterviewAnswerReview;
import ru.workbit.llm.dto.LlmInterviewFollowUp;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTopicKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import ru.workbit.llm.dto.LlmOfferProbability;
import ru.workbit.llm.service.LlmService;
import ru.workbit.util.SingleFlight;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.service.VacancyService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewServiceTest")
class InterviewServiceTest {

    private static final String ASKED_BEFORE_BLOCK = """
            Уже задавалось:
            - Что такое интерфейс?
            - Чем HashMap отличается от TreeMap?""";

    @Mock
    InterviewSessionRepository interviewSessionRepository;
    @Mock
    InterviewQuestionRepository interviewQuestionRepository;
    @Mock
    InterviewUserFeedbackRepository interviewUserFeedbackRepository;
    @Mock
    InterviewWriter interviewWriter;
    @Mock
    VacancyService vacancyService;
    @Mock
    LlmService llmService;
    @Mock
    LimitService limitService;
    @Mock
    InterviewSessionMapper interviewSessionMapper;
    @Mock
    InterviewQuestionMapper interviewQuestionMapper;
    @Mock
    InterviewReportMapper interviewReportMapper;
    @Spy
    ObjectMapper objectMapper = new JsonMapper();
    @Spy
    SingleFlight singleFlight = new SingleFlight();

    @InjectMocks
    InterviewService interviewService;

    private static LlmInterviewTopic aTopic(String name, int questions, LlmInterviewTopicKind kind) {
        return new LlmInterviewTopic(name, questions, kind);
    }

    private static InterviewSession aSession(UUID id, UUID userId, InterviewSession.Status status,
                                               UUID vacancySnapshotId, int totalQuestions) {
        return InterviewSession.builder()
                .id(id)
                .userId(userId)
                .vacancySnapshotId(vacancySnapshotId)
                .status(status)
                .totalQuestions(totalQuestions)
                .build();
    }

    private static InterviewQuestion aQuestion(UUID id, UUID parentQuestionId, int orderIndex,
                                                 boolean followUp, boolean answered, String text, String answerText) {
        return InterviewQuestion.builder()
                .id(id)
                .parentQuestionId(parentQuestionId)
                .orderIndex(orderIndex)
                .followUp(followUp)
                .kind(followUp ? InterviewQuestion.Kind.FOLLOW_UP : InterviewQuestion.Kind.MAIN)
                .topic("Тема " + orderIndex)
                .answered(answered)
                .text(text)
                .answerText(answerText)
                .build();
    }

    private static VacancyData aVacancyData(String experience) {
        return new VacancyData(VacancySnapshot.Source.HH, "123", "https://hh.ru/vacancy/123",
                "Java-разработчик", "ООО Ромашка", null, experience, List.of("Java", "Spring"), "Описание вакансии");
    }

    private static VacancySnapshotView aVacancySnapshotView(String experience) {
        return new VacancySnapshotView("123", "Java-разработчик", "ООО Ромашка", null, "https://hh.ru/vacancy/123",
                experience, List.of("Java", "Spring"), "Описание вакансии");
    }

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        private final UUID userId = UUID.randomUUID();
        private final String vacancyUrl = "https://hh.ru/vacancy/123";

        private static Stream<String> degenerateQuestions() {
            return Stream.of(null, "   ");
        }

        @Test
        @DisplayName("План с первого раза содержит вопрос в допустимом коридоре - сессия создаётся, план уходит в writer как есть")
        void createsSessionFromFirstUsablePlan() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewVacancy expectedVacancy = new LlmInterviewVacancy(vacancyData.name(), vacancyData.employer(),
                    vacancyData.experience(), vacancyData.keySkills(), vacancyData.description());
            LlmInterviewPlan rawPlan = new LlmInterviewPlan(8,
                    List.of(aTopic("SOLID", 5, LlmInterviewTopicKind.CORE),
                            aTopic("Java Core", 3, LlmInterviewTopicKind.STANDARD)),
                    "SOLID", "Расскажите про SOLID");
            when(llmService.planInterview(eq(expectedVacancy), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 8);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);

            InterviewSessionResponse expectedResponse = mock(InterviewSessionResponse.class);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0)).thenReturn(expectedResponse);

            // when
            InterviewSessionResponse result = interviewService.createSession(vacancyUrl, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo(rawPlan);
            verify(llmService, times(1)).planInterview(eq(expectedVacancy), any());
        }

        @Test
        @DisplayName("questionCount от модели меньше MIN_COUNT - после ретрая план дотягивается до MIN_COUNT")
        void clampsQuestionCountBelowMinCount() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(2,
                    List.of(aTopic("Java", 2, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), LlmInterviewPlan.MIN_COUNT);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(LlmInterviewPlan.MIN_COUNT);
        }

        @Test
        @DisplayName("questionCount от модели больше MAX_COUNT - после ретрая план срезается до MAX_COUNT")
        void clampsQuestionCountAboveMaxCount() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(20,
                    List.of(aTopic("Java", 20, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), LlmInterviewPlan.MAX_COUNT);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(LlmInterviewPlan.MAX_COUNT);
        }

        @ParameterizedTest
        @MethodSource("degenerateQuestions")
        @DisplayName("Первый план без вопроса (null/blank) - ретрай, второй план пригоден, LLM вызван дважды")
        void retriesAndCreatesSessionWhenFirstPlanHasNoQuestion(String degenerateQuestion) {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan degeneratePlan = new LlmInterviewPlan(8,
                    List.of(aTopic("Java", 8, LlmInterviewTopicKind.CORE)), "Java", degenerateQuestion);
            LlmInterviewPlan usablePlan = new LlmInterviewPlan(8,
                    List.of(aTopic("Java", 8, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(degeneratePlan, usablePlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 8);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).planInterview(any(), any());
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().question()).isEqualTo("Расскажите про Java");
        }

        @Test
        @DisplayName("Оба плана без вопроса - LlmException, LLM вызван дважды (не больше), сессия не создаётся")
        void throwsAfterRetryWhenBothPlansHaveNoQuestion() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(llmService.planInterview(any(), any())).thenReturn(new LlmInterviewPlan(8,
                    List.of(aTopic("Java", 8, LlmInterviewTopicKind.CORE)), "Java", null));

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview plan has no first question");
            verify(llmService, times(2)).planInterview(any(), any());
            verify(interviewWriter, never()).createSession(any(), any(), any(), any());
            verifyNoInteractions(interviewSessionMapper);
        }

        @Test
        @DisplayName("Сумма questions тем расходится с questionCount - после ретрая questionCount берётся из суммы")
        void alignsQuestionCountWithTopicsSumAfterRetry() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(10,
                    List.of(aTopic("Java", 4, LlmInterviewTopicKind.CORE),
                            aTopic("SQL", 2, LlmInterviewTopicKind.STANDARD)),
                    "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).planInterview(any(), any());
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(6);
            assertThat(captor.getValue().topics()).containsExactly(
                    aTopic("Java", 4, LlmInterviewTopicKind.CORE),
                    aTopic("SQL", 2, LlmInterviewTopicKind.STANDARD));
        }

        @Test
        @DisplayName("Ядро меньше половины вопросов - после ретрая вопросы добираются первой CORE-теме")
        void growsCoreTopicWhenCoreShareTooSmall() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 2, LlmInterviewTopicKind.CORE),
                            aTopic("SQL", 2, LlmInterviewTopicKind.STANDARD),
                            aTopic("Git", 2, LlmInterviewTopicKind.STANDARD)),
                    "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 8);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).planInterview(any(), any());
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(8);
            assertThat(captor.getValue().topics()).containsExactly(
                    aTopic("Java", 4, LlmInterviewTopicKind.CORE),
                    aTopic("SQL", 2, LlmInterviewTopicKind.STANDARD),
                    aTopic("Git", 2, LlmInterviewTopicKind.STANDARD));
        }

        @Test
        @DisplayName("Больше одной SOFT-темы - после ретрая остаётся первая с одним вопросом")
        void dropsExtraSoftTopicsAfterRetry() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 3, LlmInterviewTopicKind.CORE),
                            aTopic("Отношение к работе", 1, LlmInterviewTopicKind.SOFT),
                            aTopic("Мотивация", 1, LlmInterviewTopicKind.SOFT),
                            aTopic("SQL", 1, LlmInterviewTopicKind.STANDARD)),
                    "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 5);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).planInterview(any(), any());
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(5);
            assertThat(captor.getValue().topics()).containsExactly(
                    aTopic("Java", 3, LlmInterviewTopicKind.CORE),
                    aTopic("Отношение к работе", 1, LlmInterviewTopicKind.SOFT),
                    aTopic("SQL", 1, LlmInterviewTopicKind.STANDARD));
        }

        @Test
        @DisplayName("Модель не вернула тем - после ретрая план сохраняется без тем, questionCount обрезается")
        void keepsPlanWithoutTopicsAfterRetry() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(3, null, "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), LlmInterviewPlan.MIN_COUNT);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).planInterview(any(), any());
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(LlmInterviewPlan.MIN_COUNT);
            assertThat(captor.getValue().topics()).isNull();
        }

        @Test
        @DisplayName("Темы первого вопроса нет в списке - после ретрая она добавляется первой темой ядра")
        void addsMissingFirstQuestionTopicAfterRetry() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(6,
                    List.of(aTopic("SQL", 3, LlmInterviewTopicKind.CORE),
                            aTopic("Git", 3, LlmInterviewTopicKind.STANDARD)),
                    "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 7);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(7);
            assertThat(captor.getValue().topics()).containsExactly(
                    aTopic("Java", 1, LlmInterviewTopicKind.CORE),
                    aTopic("SQL", 3, LlmInterviewTopicKind.CORE),
                    aTopic("Git", 3, LlmInterviewTopicKind.STANDARD));
        }

        @Test
        @DisplayName("Ни одной CORE-темы - после ретрая ядром становится тема первого вопроса")
        void makesFirstQuestionTopicCoreWhenPlanHasNoCore() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 3, LlmInterviewTopicKind.STANDARD),
                            aTopic("SQL", 3, LlmInterviewTopicKind.STANDARD)),
                    "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().topics()).containsExactly(
                    aTopic("Java", 3, LlmInterviewTopicKind.CORE),
                    aTopic("SQL", 3, LlmInterviewTopicKind.STANDARD));
        }

        @Test
        @DisplayName("Тем больше MAX_COUNT и резать вопросы некуда - хвостовые темы отбрасываются целиком")
        void dropsTailTopicsWhenNothingLeftToTrim() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<LlmInterviewTopic> topics = new ArrayList<>();
            topics.add(aTopic("Java", 1, LlmInterviewTopicKind.CORE));
            IntStream.rangeClosed(2, 7)
                    .forEach(i -> topics.add(aTopic("Ядро " + i, 1, LlmInterviewTopicKind.CORE)));
            IntStream.rangeClosed(1, 6)
                    .forEach(i -> topics.add(aTopic("Тема " + i, 1, LlmInterviewTopicKind.STANDARD)));
            when(llmService.planInterview(any(), any()))
                    .thenReturn(new LlmInterviewPlan(13, topics, "Java", "Расскажите про Java"));

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), LlmInterviewPlan.MAX_COUNT);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> captor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), captor.capture(), any());
            assertThat(captor.getValue().questionCount()).isEqualTo(LlmInterviewPlan.MAX_COUNT);
            assertThat(captor.getValue().topics())
                    .hasSize(LlmInterviewPlan.MAX_COUNT)
                    .extracting(LlmInterviewTopic::name)
                    .contains("Java")
                    .doesNotContain("Тема 6");
        }

        @Test
        @DisplayName("Есть незавершённая сессия по вакансии - ConflictException, LLM и writer не вызываются")
        void throwsWhenUnfinishedInterviewExistsForVacancy() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<UUID> snapshotIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(snapshotIds);
            when(interviewSessionRepository.existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                    userId, snapshotIds, InterviewSession.Status.COMPLETED)).thenReturn(true);

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Unfinished interview exists");
            verifyNoInteractions(llmService, interviewWriter);
        }

        @Test
        @DisplayName("Снапшоты вакансии есть, но незавершённых сессий нет - сессия создаётся штатно")
        void createsSessionWhenSnapshotsExistButNoUnfinishedInterview() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<UUID> snapshotIds = List.of(UUID.randomUUID());
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(snapshotIds);
            when(interviewSessionRepository.existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                    userId, snapshotIds, InterviewSession.Status.COMPLETED)).thenReturn(false);

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), any(), any());
            verify(llmService, times(1)).planInterview(any(), any());
        }

        @Test
        @DisplayName("Снапшотов по sourceId нет - exists не вызывается, сессия создаётся штатно")
        void createsSessionWhenNoSnapshotsForVacancy() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(List.of());

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewSessionRepository, never())
                    .existsByUserIdAndVacancySnapshotIdInAndStatusNot(any(), any(), any());
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), any(), any());
        }

        @Test
        @DisplayName("По вакансии уже были интервью - их основные вопросы уходят блоком и в модель, и в сессию")
        void passesQuestionsOfPastInterviewsAsAskedBefore() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<UUID> snapshotIds = List.of(UUID.randomUUID());
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(snapshotIds);
            when(interviewSessionRepository.existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                    userId, snapshotIds, InterviewSession.Status.COMPLETED)).thenReturn(false);
            when(interviewQuestionRepository.findQuestionTexts(userId, snapshotIds,
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN))
                    .thenReturn(List.of("Что такое интерфейс?", "Чем HashMap отличается от TreeMap?"));

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService).planInterview(any(), eq(ASKED_BEFORE_BLOCK));
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), any(), eq(ASKED_BEFORE_BLOCK));
        }

        @Test
        @DisplayName("Завершённых интервью по вакансии не было - блока «уже задавалось» нет")
        void passesNoAskedBeforeWhenNoPastQuestions() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<UUID> snapshotIds = List.of(UUID.randomUUID());
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(snapshotIds);
            when(interviewSessionRepository.existsByUserIdAndVacancySnapshotIdInAndStatusNot(
                    userId, snapshotIds, InterviewSession.Status.COMPLETED)).thenReturn(false);
            when(interviewQuestionRepository.findQuestionTexts(userId, snapshotIds,
                    InterviewSession.Status.COMPLETED, InterviewQuestion.Kind.MAIN)).thenReturn(List.of());

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService).planInterview(any(), isNull());
            verify(interviewWriter).createSession(eq(vacancyData), eq(userId), any(), isNull());
        }

        @Test
        @DisplayName("Снапшотов по вакансии нет - за прошлыми вопросами в базу не ходим")
        void skipsAskedBeforeLookupWithoutSnapshots() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(List.of());

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewQuestionRepository, never()).findQuestionTexts(any(), any(), any(), any());
            verify(llmService).planInterview(any(), isNull());
        }

        @Test
        @DisplayName("Проверка квоты интервью выполняется до запроса плана у LLM")
        void checksQuotaBeforeRequestingPlan() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan plan = new LlmInterviewPlan(6,
                    List.of(aTopic("Java", 6, LlmInterviewTopicKind.CORE)), "Java", "Расскажите про Java");
            when(llmService.planInterview(any(), any())).thenReturn(plan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 6);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            InOrder order = inOrder(limitService, llmService);
            order.verify(limitService).check(userId, UsageEvent.Operation.INTERVIEW);
            order.verify(llmService).planInterview(any(), any());
        }

        @Test
        @DisplayName("Квота интервью исчерпана - PaymentRequiredException пробрасывается, LLM не вызывается, сессия не создаётся")
        void throwsWhenInterviewQuotaExhausted() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            doThrow(new PaymentRequiredException("Not enough limits"))
                    .when(limitService).check(userId, UsageEvent.Operation.INTERVIEW);

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Not enough limits");
            verifyNoInteractions(llmService, interviewWriter);
        }

        @Test
        @DisplayName("Идёт через SingleFlight с ключом interview.create (userId, vacancyUrl)")
        void goesThroughSingleFlightWithUserAndVacancyUrlKey() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewPlan rawPlan = new LlmInterviewPlan(8,
                    List.of(aTopic("SOLID", 5, LlmInterviewTopicKind.CORE),
                            aTopic("Java Core", 3, LlmInterviewTopicKind.STANDARD)),
                    "SOLID", "Расскажите про SOLID");
            when(llmService.planInterview(any(), any())).thenReturn(rawPlan);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 8);
            when(interviewWriter.createSession(eq(vacancyData), eq(userId), any(), any())).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(singleFlight).run(eq(new SingleFlight.Key(
                    "interview.create", List.of(userId, vacancyUrl))), any());
        }
    }

    @Nested
    @DisplayName("Get")
    class Get {

        @Test
        @DisplayName("Сессия найдена - возвращает ответ с числом отвеченных основных вопросов")
        void returnsResponseWithAnsweredCount() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 10);
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            when(interviewQuestionRepository.countBySessionIdAndFollowUpFalseAndAnsweredTrue(sessionId)).thenReturn(3L);

            InterviewSessionResponse expectedResponse = mock(InterviewSessionResponse.class);
            when(interviewSessionMapper.toResponse(session, vacancy, 3)).thenReturn(expectedResponse);

            // when
            InterviewSessionResponse result = interviewService.get(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.get(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(vacancyService, interviewQuestionRepository, interviewSessionMapper);
        }
    }

    @Nested
    @DisplayName("NextQuestion")
    class NextQuestion {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();
        private final UUID vacancySnapshotId = UUID.randomUUID();

        private InterviewSession activeSession(int totalQuestions) {
            return aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS, vacancySnapshotId, totalQuestions);
        }

        private static InterviewQuestion aMain(UUID id, int orderIndex, boolean answered, boolean followUpChecked) {
            return InterviewQuestion.builder()
                    .id(id)
                    .orderIndex(orderIndex)
                    .kind(InterviewQuestion.Kind.MAIN)
                    .topic("Тема " + orderIndex)
                    .text("Основной вопрос " + orderIndex)
                    .answered(answered)
                    .answerText(answered ? "Ответ " + orderIndex : null)
                    .followUpChecked(followUpChecked)
                    .build();
        }

        private static InterviewQuestion aChild(UUID id, UUID parentId, InterviewQuestion.Kind kind, int orderIndex,
                                                  boolean answered, boolean followUpChecked) {
            return InterviewQuestion.builder()
                    .id(id)
                    .parentQuestionId(parentId)
                    .followUp(true)
                    .kind(kind)
                    .orderIndex(orderIndex)
                    .topic("Тема уточнения")
                    .text(kind + " вопрос")
                    .answered(answered)
                    .answerText(answered ? "Ответ на " + kind : null)
                    .followUpChecked(followUpChecked)
                    .build();
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException, зависимости ниже не дёргаются")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(interviewQuestionRepository, llmService, vacancyService, interviewWriter, interviewQuestionMapper);
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException")
        void throwsWhenSessionOwnedByAnotherUser() {
            // given
            InterviewSession session = aSession(sessionId, UUID.randomUUID(), InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 5);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(interviewQuestionRepository, llmService, vacancyService, interviewWriter, interviewQuestionMapper);
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException, зависимости ниже не дёргаются")
        void throwsWhenSessionCompleted() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    vacancySnapshotId, 5);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(interviewQuestionRepository, llmService, vacancyService, interviewWriter, interviewQuestionMapper);
        }

        @Test
        @DisplayName("Есть неотвеченный вопрос - возвращается он, к модели не ходим")
        void returnsUnansweredQuestionWithoutCallingModel() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion unansweredMain = aMain(UUID.randomUUID(), 2, false, false);
            session.setQuestions(List.of(unansweredMain));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(unansweredMain)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llmService, vacancyService, interviewWriter);
        }

        @Test
        @DisplayName("Нет ни неотвеченных, ни отвеченных вопросов - ConflictException, к модели не ходим")
        void throwsConflictWhenNoQuestionsAtAll() {
            // given
            InterviewSession session = activeSession(5);
            session.setQuestions(List.of());
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verifyNoInteractions(llmService, vacancyService, interviewWriter, interviewQuestionMapper);
        }

        @Test
        @DisplayName("По последнему отвеченному вопросу модель уже ходила - ConflictException, повторно к модели не ходим")
        void throwsConflictWhenLastAnsweredAlreadyChecked() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, true);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verifyNoInteractions(llmService, vacancyService, interviewWriter);
        }

        @Test
        @DisplayName("Модель вернула MAIN, основных задано меньше total - сохраняется новый основной вопрос")
        void savesNewMainQuestionWhenBelowTotal() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Расскажите про Spring", "Spring");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.MAIN, step.question(), step.topic()))
                    .thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewWriter, never()).closeQuestioning(any(), any());
        }

        @Test
        @DisplayName("Модель вернула MAIN, основные исчерпаны - интервью завершается, вопрос не сохраняется")
        void closesQuestioningWhenMainExhausted() {
            // given
            InterviewSession session = activeSession(1);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, null, null);
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(interviewWriter).closeQuestioning(main.getId(), null);
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Модель вернула MAIN с прощальной репликой при исчерпанных основных - реплика сохраняется в сессии")
        void savesClosingRemarkWhenModelSaysGoodbye() {
            // given
            InterviewSession session = activeSession(1);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "На этом всё, спасибо за разговор.",
                    null);
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(interviewWriter).closeQuestioning(main.getId(), "На этом всё, спасибо за разговор.");
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Блок «уже задавалось» из сессии уходит модели вместе с историей беседы")
        void passesSessionAskedBeforeToModel() {
            // given
            InterviewSession session = activeSession(5);
            session.setAskedBefore(ASKED_BEFORE_BLOCK);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Расскажите про Spring", "Spring");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.MAIN, step.question(), step.topic()))
                    .thenReturn(Optional.of(mock(InterviewQuestionResponse.class)));

            // when
            interviewService.nextQuestion(sessionId, userId);

            // then
            verify(llmService).nextInterviewStep(any(), any(), any(), any(), eq(ASKED_BEFORE_BLOCK));
        }

        @Test
        @DisplayName("Модель вернула FOLLOW_UP, у кейса ещё нет уточнения - сохраняется как FOLLOW_UP")
        void savesFollowUpWhenCaseHasNoFollowUpYet() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "А что насчёт Java?", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.FOLLOW_UP, step.question(), step.topic()))
                    .thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Модель вернула FOLLOW_UP, у кейса уже есть уточнение - трактуется как MAIN, основных не хватает")
        void treatsFollowUpAsMainWhenCaseAlreadyHasFollowUp() {
            // given
            InterviewSession session = activeSession(5);
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            InterviewQuestion existingFollowUp = aChild(UUID.randomUUID(), mainId, InterviewQuestion.Kind.FOLLOW_UP,
                    1, true, false);
            session.setQuestions(List.of(main, existingFollowUp));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "Ещё уточнение?", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(existingFollowUp.getId(), InterviewQuestion.Kind.MAIN,
                    step.question(), step.topic())).thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("FOLLOW_UP трактуется как MAIN, но основные уже исчерпаны - интервью завершается")
        void closesQuestioningWhenFollowUpTreatedAsMainAndMainExhausted() {
            // given
            InterviewSession session = activeSession(1);
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            InterviewQuestion existingFollowUp = aChild(UUID.randomUUID(), mainId, InterviewQuestion.Kind.FOLLOW_UP,
                    1, true, false);
            session.setQuestions(List.of(main, existingFollowUp));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "Ещё уточнение?", "Тема");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(interviewWriter).closeQuestioning(existingFollowUp.getId(), null);
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Модель вернула CLARIFICATION - сохраняется без лимита, даже если у кейса уже есть уточнение")
        void savesClarificationWithoutLimit() {
            // given
            InterviewSession session = activeSession(5);
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            InterviewQuestion existingClarification = aChild(UUID.randomUUID(), mainId,
                    InterviewQuestion.Kind.CLARIFICATION, 1, true, false);
            session.setQuestions(List.of(main, existingClarification));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.CLARIFICATION, "Поясните вопрос ещё раз",
                    "Тема");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(existingClarification.getId(), InterviewQuestion.Kind.CLARIFICATION,
                    step.question(), step.topic())).thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Модель вернула REDIRECT - сохраняется как REDIRECT, лимита на возвраты нет")
        void savesRedirect() {
            // given
            InterviewSession session = activeSession(5);
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            InterviewQuestion redirect1 = aChild(UUID.randomUUID(), mainId, InterviewQuestion.Kind.REDIRECT,
                    1, true, false);
            session.setQuestions(List.of(main, redirect1));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.REDIRECT, "Давайте вернёмся к вопросу",
                    "Тема");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(redirect1.getId(), InterviewQuestion.Kind.REDIRECT,
                    step.question(), step.topic())).thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Модель вернула END - беседа обрывается, прощальная реплика уходит в сессию")
        void closesQuestioningWithClosingRemarkOnEnd() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.END, "Похоже, разговор не складывается. Давайте на этом остановимся.",
                    "Тема");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(interviewWriter).closeQuestioning(main.getId(), step.question());
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Модель вернула END без прощальной реплики - беседа обрывается, реплика не сохраняется")
        void closesQuestioningOnEndWithoutClosingRemark() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.END, null, null);
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(llmService).nextInterviewStep(any(), any(), any(), any(), any());
            verify(interviewWriter).closeQuestioning(main.getId(), null);
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Шесть реплик на одном основном вопросе - опрос закрывается, к модели не идём")
        void closesQuestioningWhenCaseHitsReplyLimit() {
            // given
            InterviewSession session = activeSession(5);
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            List<InterviewQuestion> questions = new ArrayList<>(List.of(main));
            IntStream.rangeClosed(1, 5).forEach(i -> questions.add(aChild(UUID.randomUUID(), mainId,
                    InterviewQuestion.Kind.REDIRECT, i, true, false)));
            session.setQuestions(questions);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verify(interviewWriter).closeQuestioning(questions.getLast().getId(), null);
            verify(interviewWriter, never()).saveStep(any(), any(), any(), any());
            verifyNoInteractions(llmService, vacancyService);
        }

        @Test
        @DisplayName("Первая реплика модели вырожденная - ретрай, вторая пригодна, LLM вызван дважды")
        void retriesWhenFirstStepIsDegenerate() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep degenerate = new LlmInterviewStep(null, null, null);
            LlmInterviewStep usable = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Расскажите про JVM", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(degenerate, usable);

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.MAIN, usable.question(), usable.topic()))
                    .thenReturn(Optional.of(expected));

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(llmService, times(2)).nextInterviewStep(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Обе реплики модели вырожденные - LlmException, LLM вызван дважды (не больше)")
        void throwsAfterRetryWhenBothStepsAreDegenerate() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep degenerate = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "   ", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(degenerate);

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interview step has no question");
            verify(llmService, times(2)).nextInterviewStep(any(), any(), any(), any(), any());
            verifyNoInteractions(interviewWriter);
        }

        @Test
        @DisplayName("Конкурентная гонка при сохранении реплики - возвращается уже сохранённый параллельным запросом вопрос")
        void resolvesRaceByReturningNextUnansweredAfterConflict() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Расскажите про GC", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.MAIN, step.question(), step.topic()))
                    .thenThrow(new DataIntegrityViolationException("duplicate step"));

            InterviewQuestion raceQuestion = aMain(UUID.randomUUID(), 2, false, false);
            when(interviewQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.of(raceQuestion));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(raceQuestion)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Транскрипт для модели собран из снапшота вакансии, плана сессии и истории беседы")
        void buildsRequestFromVacancyPlanAndHistory() {
            // given
            InterviewSession session = activeSession(5);
            session.setPlanTopics("[{\"name\":\"SOLID\",\"questions\":3,\"kind\":\"CORE\"},"
                    + "{\"name\":\"Java Core\",\"questions\":2,\"kind\":\"STANDARD\"}]");
            UUID mainId = UUID.randomUUID();
            InterviewQuestion main = aMain(mainId, 1, true, false);
            main.setText("Расскажите про SOLID");
            main.setTopic("SOLID");
            main.setAnswerText("Мой ответ про SOLID");
            InterviewQuestion followUp = aChild(UUID.randomUUID(), mainId, InterviewQuestion.Kind.FOLLOW_UP,
                    1, true, false);
            followUp.setText("А что такое OCP?");
            followUp.setTopic("SOLID");
            followUp.setAnswerText("Открыт для расширения, закрыт для изменения");
            session.setQuestions(List.of(main, followUp));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.CLARIFICATION, "Поясните ещё раз?", "SOLID");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);
            when(interviewWriter.saveStep(followUp.getId(), InterviewQuestion.Kind.CLARIFICATION,
                    step.question(), step.topic())).thenReturn(Optional.of(mock(InterviewQuestionResponse.class)));

            // when
            interviewService.nextQuestion(sessionId, userId);

            // then
            ArgumentCaptor<LlmInterviewVacancy> vacancyCaptor = ArgumentCaptor.forClass(LlmInterviewVacancy.class);
            ArgumentCaptor<LlmInterviewPlan> planCaptor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LlmInterviewTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<String> lastAnswerCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmService).nextInterviewStep(vacancyCaptor.capture(), planCaptor.capture(),
                    historyCaptor.capture(), lastAnswerCaptor.capture(), any());

            assertThat(vacancyCaptor.getValue()).isEqualTo(new LlmInterviewVacancy(
                    vacancy.name(), vacancy.employer(), vacancy.experience(), vacancy.keySkills(), vacancy.description()));
            assertThat(planCaptor.getValue()).isEqualTo(new LlmInterviewPlan(
                    session.getTotalQuestions(),
                    List.of(aTopic("SOLID", 3, LlmInterviewTopicKind.CORE),
                            aTopic("Java Core", 2, LlmInterviewTopicKind.STANDARD)),
                    main.getTopic(), main.getText()));
            assertThat(historyCaptor.getValue()).containsExactly(new LlmInterviewTurn(main.getAnswerText(),
                    new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, followUp.getText(), followUp.getTopic())));
            assertThat(lastAnswerCaptor.getValue()).isEqualTo(followUp.getAnswerText());
        }

        @Test
        @DisplayName("Сессия без тем плана (легаси) - в план для модели уходит null")
        void buildsPlanWithoutTopicsForLegacySession() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion main = aMain(UUID.randomUUID(), 1, true, false);
            session.setQuestions(List.of(main));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewStep step = new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Расскажите про JVM", "Java");
            when(llmService.nextInterviewStep(any(), any(), any(), any(), any())).thenReturn(step);
            when(interviewWriter.saveStep(main.getId(), InterviewQuestion.Kind.MAIN, step.question(), step.topic()))
                    .thenReturn(Optional.of(mock(InterviewQuestionResponse.class)));

            // when
            interviewService.nextQuestion(sessionId, userId);

            // then
            ArgumentCaptor<LlmInterviewPlan> planCaptor = ArgumentCaptor.forClass(LlmInterviewPlan.class);
            verify(llmService).nextInterviewStep(any(), planCaptor.capture(), any(), any(), any());
            assertThat(planCaptor.getValue().topics()).isNull();
        }

        @Test
        @DisplayName("Идёт через SingleFlight с ключом interview.next (sessionId, userId)")
        void goesThroughSingleFlightWithSessionAndUserKey() {
            // given
            InterviewSession session = activeSession(5);
            InterviewQuestion unansweredMain = aMain(UUID.randomUUID(), 2, false, false);
            session.setQuestions(List.of(unansweredMain));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            when(interviewQuestionMapper.toDto(unansweredMain)).thenReturn(mock(InterviewQuestionResponse.class));

            // when
            interviewService.nextQuestion(sessionId, userId);

            // then
            verify(singleFlight).run(eq(new SingleFlight.Key("interview.next", List.of(sessionId, userId))), any());
        }
    }

    @Nested
    @DisplayName("GetAnsweredQuestions")
    class GetAnsweredQuestions {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        @Test
        @DisplayName("Возвращает историю кейсами: основной вопрос и сразу его отвеченное уточнение, порядок по orderIndex, неотвеченные не попадают")
        void returnsAnsweredCasesOrderedByOrderIndexRegardlessOfStorageOrder() {
            // given
            InterviewQuestion mainA = aQuestion(UUID.randomUUID(), null, 1, false, true,
                    "Основной вопрос A", "Ответ A");
            InterviewQuestion followUpA = aQuestion(UUID.randomUUID(), mainA.getId(), 1, true, true,
                    "Уточнение к A", "Ответ на уточнение A");
            InterviewQuestion mainB = aQuestion(UUID.randomUUID(), null, 2, false, true,
                    "Основной вопрос B", "Ответ B");
            InterviewQuestion unanswered = aQuestion(UUID.randomUUID(), null, 3, false, false,
                    "Неотвеченный основной вопрос", null);

            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    UUID.randomUUID(), 3);
            session.setQuestions(List.of(mainB, unanswered, followUpA, mainA));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            InterviewQuestionResponse mainAResponse = mock(InterviewQuestionResponse.class);
            InterviewQuestionResponse followUpAResponse = mock(InterviewQuestionResponse.class);
            InterviewQuestionResponse mainBResponse = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(mainA)).thenReturn(mainAResponse);
            when(interviewQuestionMapper.toDto(followUpA)).thenReturn(followUpAResponse);
            when(interviewQuestionMapper.toDto(mainB)).thenReturn(mainBResponse);

            // when
            List<InterviewQuestionResponse> result = interviewService.getAnsweredQuestions(sessionId, userId);

            // then
            assertThat(result).containsExactly(mainAResponse, followUpAResponse, mainBResponse);
        }

        @Test
        @DisplayName("Нет ни одного отвеченного вопроса - пустой список")
        void returnsEmptyListWhenNothingAnswered() {
            // given
            InterviewQuestion unanswered = aQuestion(UUID.randomUUID(), null, 1, false, false,
                    "Неотвеченный вопрос", null);
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 1);
            session.setQuestions(List.of(unanswered));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when
            List<InterviewQuestionResponse> result = interviewService.getAnsweredQuestions(sessionId, userId);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(interviewQuestionMapper);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.getAnsweredQuestions(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(interviewQuestionMapper);
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException")
        void throwsWhenSessionOwnedByAnotherUser() {
            // given
            InterviewSession session = aSession(sessionId, UUID.randomUUID(), InterviewSession.Status.IN_PROGRESS,
                    UUID.randomUUID(), 3);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.getAnsweredQuestions(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(interviewQuestionMapper);
        }
    }

    @Nested
    @DisplayName("SubmitAnswer")
    class SubmitAnswer {

        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final UUID questionId = UUID.randomUUID();

        private InterviewQuestion questionInSession(InterviewSession.Status sessionStatus, boolean answered) {
            InterviewSession session = aSession(sessionId, userId, sessionStatus, UUID.randomUUID(), 10);
            InterviewQuestion question = aQuestion(questionId, null, 1, false, answered, "Вопрос", null);
            question.setSession(session);
            return question;
        }

        @Test
        @DisplayName("CREATED -> IN_PROGRESS, поля ответа проставляются")
        void transitionsCreatedToInProgressAndSetsAnswerFields() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.CREATED, false);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, questionId, "Мой ответ");

            // when
            interviewService.submitAnswer(request);

            // then
            assertThat(question.getAnswerText()).isEqualTo("Мой ответ");
            assertThat(question.isAnswered()).isTrue();
            assertThat(question.getAnsweredAt()).isNotNull();
            assertThat(question.getSession().getStatus()).isEqualTo(InterviewSession.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("Сессия уже IN_PROGRESS - статус не меняется")
        void keepsInProgressStatusWhenAlreadyInProgress() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.IN_PROGRESS, false);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, questionId, "Мой ответ");

            // when
            interviewService.submitAnswer(request);

            // then
            assertThat(question.getSession().getStatus()).isEqualTo(InterviewSession.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, questionId, "Мой ответ");

            // when / then
            assertThatThrownBy(() -> interviewService.submitAnswer(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
        }

        @Test
        @DisplayName("Вопрос принадлежит другому пользователю - ForbiddenException, ответ не сохраняется")
        void throwsWhenOwnershipMismatch() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.CREATED, false);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(UUID.randomUUID(), sessionId, questionId, "Чужой ответ");

            // when / then
            assertThatThrownBy(() -> interviewService.submitAnswer(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");
            assertThat(question.isAnswered()).isFalse();
        }

        @Test
        @DisplayName("Вопрос принадлежит другой сессии - ConflictException")
        void throwsWhenSessionMismatch() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.CREATED, false);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, UUID.randomUUID(), questionId, "Мой ответ");

            // when / then
            assertThatThrownBy(() -> interviewService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.COMPLETED, false);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, questionId, "Мой ответ");

            // when / then
            assertThatThrownBy(() -> interviewService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @Test
        @DisplayName("Вопрос уже отвечен - ConflictException")
        void throwsWhenQuestionAlreadyAnswered() {
            // given
            InterviewQuestion question = questionInSession(InterviewSession.Status.IN_PROGRESS, true);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, questionId, "Мой ответ");

            // when / then
            assertThatThrownBy(() -> interviewService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question already answered");
        }
    }

    @Nested
    @DisplayName("SubmitQuestionFeedback")
    class SubmitQuestionFeedback {

        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final UUID questionId = UUID.randomUUID();

        private InterviewQuestion questionInSession(UUID ownerId) {
            InterviewSession session = aSession(sessionId, ownerId, InterviewSession.Status.IN_PROGRESS,
                    UUID.randomUUID(), 5);
            InterviewQuestion question = aQuestion(questionId, null, 1, false, true, "Вопрос", "Ответ");
            question.setSession(session);
            return question;
        }

        @Test
        @DisplayName("Валидный запрос - сохраняет фидбэк с sessionId/questionId и полями из запроса")
        void savesFeedbackWithRequestFields() {
            // given
            InterviewQuestion question = questionInSession(userId);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(
                    InterviewUserFeedback.Vote.DOWN, List.of("Оценка занижена"), "Комментарий");

            // when
            interviewService.submitQuestionFeedback(sessionId, questionId, userId, request);

            // then
            ArgumentCaptor<InterviewUserFeedback> captor = ArgumentCaptor.forClass(InterviewUserFeedback.class);
            verify(interviewUserFeedbackRepository).save(captor.capture());
            InterviewUserFeedback saved = captor.getValue();
            assertThat(saved.getSessionId()).isEqualTo(sessionId);
            assertThat(saved.getQuestionId()).isEqualTo(questionId);
            assertThat(saved.getVote()).isEqualTo(InterviewUserFeedback.Vote.DOWN);
            assertThat(saved.getReasons()).containsExactly("Оценка занижена");
            assertThat(saved.getComment()).isEqualTo("Комментарий");
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> interviewService.submitQuestionFeedback(sessionId, questionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verifyNoInteractions(interviewUserFeedbackRepository);
        }

        @Test
        @DisplayName("Вопрос принадлежит другому пользователю - ForbiddenException")
        void throwsWhenQuestionOwnedByAnotherUser() {
            // given
            InterviewQuestion question = questionInSession(UUID.randomUUID());
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> interviewService.submitQuestionFeedback(sessionId, questionId, userId, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");
            verifyNoInteractions(interviewUserFeedbackRepository);
        }

        @Test
        @DisplayName("Вопрос принадлежит другой сессии, чем в запросе - ConflictException")
        void throwsWhenQuestionBelongsToAnotherSession() {
            // given
            InterviewQuestion question = questionInSession(userId);
            when(interviewQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> interviewService.submitQuestionFeedback(
                    UUID.randomUUID(), questionId, userId, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");
            verifyNoInteractions(interviewUserFeedbackRepository);
        }
    }

    @Nested
    @DisplayName("SubmitReportFeedback")
    class SubmitReportFeedback {

        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();

        @Test
        @DisplayName("Отчёт сформирован - сохраняет фидбэк с questionId=null")
        void savesFeedbackWithNullQuestionId() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    UUID.randomUUID(), 5);
            session.setReport(InterviewReport.builder()
                    .avgScore(4.0).offerProbability(InterviewReport.OfferProbability.HIGH)
                    .overallFeedback("Фидбэк").build());
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), "Отлично");

            // when
            interviewService.submitReportFeedback(sessionId, userId, request);

            // then
            ArgumentCaptor<InterviewUserFeedback> captor = ArgumentCaptor.forClass(InterviewUserFeedback.class);
            verify(interviewUserFeedbackRepository).save(captor.capture());
            InterviewUserFeedback saved = captor.getValue();
            assertThat(saved.getSessionId()).isEqualTo(sessionId);
            assertThat(saved.getQuestionId()).isNull();
            assertThat(saved.getVote()).isEqualTo(InterviewUserFeedback.Vote.UP);
            assertThat(saved.getComment()).isEqualTo("Отлично");
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> interviewService.submitReportFeedback(sessionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(interviewUserFeedbackRepository);
        }

        @Test
        @DisplayName("Отчёт ещё не сформирован - NotFoundException")
        void throwsWhenReportNotYetGenerated() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    UUID.randomUUID(), 5);
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            FeedbackRequest request = new FeedbackRequest(InterviewUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> interviewService.submitReportFeedback(sessionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Report not found");
            verifyNoInteractions(interviewUserFeedbackRepository);
        }
    }

    @Nested
    @DisplayName("CreateReport")
    class CreateReport {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();
        private final UUID vacancySnapshotId = UUID.randomUUID();

        @Test
        @DisplayName("Сессия не найдена - NotFoundException, дальше по цепочке ничего не дёргается")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(vacancyService, llmService, interviewWriter);
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException (IDOR не палит существование)")
        void throwsWhenSessionBelongsToAnotherUser() {
            // given
            InterviewSession session = aSession(sessionId, UUID.randomUUID(), InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 2);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(vacancyService, llmService, interviewWriter);
        }

        @Test
        @DisplayName("Сессия уже завершена - отдаёт готовый отчёт, LLM и запись не трогаются")
        void returnsExistingReportWhenSessionAlreadyCompleted() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    vacancySnapshotId, 2);
            session.setQuestions(List.of());
            InterviewReport report = InterviewReport.builder().id(UUID.randomUUID()).build();
            session.setReport(report);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewReportMapper.toResponse(eq(report), eq(session), any())).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isSameAs(expectedResponse);
            verifyNoInteractions(vacancyService, llmService, interviewWriter);
        }

        @Test
        @DisplayName("Отвечены не все основные вопросы - ConflictException, отчёт не строится")
        void throwsWhenNotAllMainQuestionsAnswered() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 3);
            session.setQuestions(List.of(
                    aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1"),
                    aQuestion(UUID.randomUUID(), null, 2, false, false, "Вопрос 2", null),
                    aQuestion(UUID.randomUUID(), null, 3, false, true, "Вопрос 3", "Ответ 3")));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Not all questions answered");
            verifyNoInteractions(vacancyService, llmService, interviewWriter);
        }

        @Test
        @DisplayName("Все основные вопросы отвечены - кейсы группируются, запрос к LLM собран, writer завершает отчёт")
        void buildsReportRequestFromGroupedCasesAndDelegatesToWriter() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 2);
            UUID main1Id = UUID.randomUUID();
            UUID main2Id = UUID.randomUUID();
            InterviewQuestion main1 = aQuestion(main1Id, null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion followUp1 = aQuestion(UUID.randomUUID(), main1Id, 1, true, true,
                    "Уточнение к вопросу 1", "Ответ на уточнение");
            InterviewQuestion main2 = aQuestion(main2Id, null, 2, false, true, "Вопрос 2", "Ответ 2");
            session.setQuestions(List.of(main1, followUp1, main2));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ по первому кейсу", 4),
                            new LlmInterviewAnswerReview(2, "Хороший ответ по второму кейсу", 5)),
                    LlmOfferProbability.MEDIUM, "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(any(), any())).thenReturn(llmReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, llmReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            LlmInterviewVacancy expectedVacancy = new LlmInterviewVacancy(vacancy.name(), vacancy.employer(),
                    vacancy.experience(), vacancy.keySkills(), vacancy.description());
            verify(llmService, times(1)).createInterviewReport(expectedVacancy, List.of(
                    new LlmInterviewAnswer(1, main1.getTopic(), main1.getText(), main1.getAnswerText(),
                            List.of(new LlmInterviewFollowUp(LlmInterviewStepKind.FOLLOW_UP,
                                    followUp1.getText(), followUp1.getAnswerText()))),
                    new LlmInterviewAnswer(2, main2.getTopic(), main2.getText(), main2.getAnswerText(), List.of())));
        }

        @Test
        @DisplayName("Конкурентное завершение сессии - вместо DataIntegrityViolationException отдаёт отчёт, записанный победителем")
        void returnsWinnersReportWhenWriterDetectsConcurrentCompletion() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 1);
            session.setQuestions(List.of(aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1")));
            InterviewSession completed = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    vacancySnapshotId, 1);
            completed.setQuestions(List.of());
            InterviewReport report = InterviewReport.builder().id(UUID.randomUUID()).build();
            completed.setReport(report);
            when(interviewSessionRepository.findWithQuestionsById(sessionId))
                    .thenReturn(Optional.of(session), Optional.of(completed));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ", 4)),
                    LlmOfferProbability.MEDIUM, "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(any(), any())).thenReturn(llmReport);
            when(interviewWriter.completeReport(sessionId, llmReport))
                    .thenThrow(new DataIntegrityViolationException("already completed"));
            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewReportMapper.toResponse(eq(report), eq(completed), any())).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        @DisplayName("Первый ответ LLM вырожденный - ретрай возвращает пригодный отчёт, LLM вызван дважды с теми же аргументами")
        void retriesReportWhenFirstResponseIsDegenerate() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 2);
            InterviewQuestion main1 = aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion main2 = aQuestion(UUID.randomUUID(), null, 2, false, true, "Вопрос 2", "Ответ 2");
            session.setQuestions(List.of(main1, main2));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewReport degenerateReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Разбор", 3)),
                    LlmOfferProbability.MEDIUM, "коротко", null, null);
            LlmInterviewReport usableReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ по первому кейсу", 4),
                            new LlmInterviewAnswerReview(2, "Хороший ответ по второму кейсу", 5)),
                    LlmOfferProbability.MEDIUM, "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(any(), any()))
                    .thenReturn(degenerateReport, usableReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmInterviewVacancy> vacancyCaptor = ArgumentCaptor.forClass(LlmInterviewVacancy.class);
            ArgumentCaptor<List<LlmInterviewAnswer>> answersCaptor = ArgumentCaptor.captor();
            verify(llmService, times(2)).createInterviewReport(vacancyCaptor.capture(), answersCaptor.capture());
            assertThat(vacancyCaptor.getAllValues()).hasSize(2);
            assertThat(vacancyCaptor.getAllValues().get(0)).isEqualTo(vacancyCaptor.getAllValues().get(1));
            assertThat(answersCaptor.getAllValues().get(0)).isEqualTo(answersCaptor.getAllValues().get(1));
            verify(interviewWriter).completeReport(sessionId, usableReport);
        }

        @Test
        @DisplayName("Первый ответ LLM пригодный - ретрай не требуется, ровно один вызов")
        void doesNotRetryWhenFirstResponseIsUsable() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 2);
            InterviewQuestion main1 = aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion main2 = aQuestion(UUID.randomUUID(), null, 2, false, true, "Вопрос 2", "Ответ 2");
            session.setQuestions(List.of(main1, main2));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewReport usableReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ по первому кейсу", 4),
                            new LlmInterviewAnswerReview(2, "Хороший ответ по второму кейсу", 5)),
                    LlmOfferProbability.MEDIUM, "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(any(), any())).thenReturn(usableReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(llmService, times(1)).createInterviewReport(any(), any());
            verify(interviewWriter).completeReport(sessionId, usableReport);
        }

        @Test
        @DisplayName("Оба ответа LLM вырожденные - вызван дважды (не больше), writer-у уходит второй ответ")
        void delegatesSecondDegenerateResponseWhenBothAttemptsAreDegenerate() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 2);
            InterviewQuestion main1 = aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion main2 = aQuestion(UUID.randomUUID(), null, 2, false, true, "Вопрос 2", "Ответ 2");
            session.setQuestions(List.of(main1, main2));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewReport firstDegenerate = new LlmInterviewReport(
                    List.of(), LlmOfferProbability.MEDIUM, "коротко", null, null);
            LlmInterviewReport secondDegenerate = new LlmInterviewReport(
                    List.of(), LlmOfferProbability.MEDIUM, "тоже коротко", null, null);
            when(llmService.createInterviewReport(any(), any()))
                    .thenReturn(firstDegenerate, secondDegenerate);
            when(interviewWriter.completeReport(sessionId, secondDegenerate))
                    .thenThrow(new LlmException("Interview report has no usable overall feedback"));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(LlmException.class);
            verify(llmService, times(2)).createInterviewReport(any(), any());
            verify(interviewWriter).completeReport(sessionId, secondDegenerate);
        }

        @Test
        @DisplayName("Идёт через SingleFlight с ключом interview.finish (sessionId, userId)")
        void goesThroughSingleFlightWithSessionAndUserKey() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    vacancySnapshotId, 2);
            session.setQuestions(List.of());
            InterviewReport report = InterviewReport.builder().id(UUID.randomUUID()).build();
            session.setReport(report);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            when(interviewReportMapper.toResponse(eq(report), eq(session), any()))
                    .thenReturn(mock(InterviewReportResponse.class));

            // when
            interviewService.createReport(sessionId, userId);

            // then
            verify(singleFlight).run(eq(new SingleFlight.Key("interview.finish", List.of(sessionId, userId))), any());
        }
    }

    @Nested
    @DisplayName("GetReport")
    class GetReport {

        private final UUID sessionId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.getReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException")
        void throwsWhenSessionBelongsToAnotherUser() {
            // given
            InterviewSession session = aSession(sessionId, UUID.randomUUID(), InterviewSession.Status.COMPLETED,
                    UUID.randomUUID(), 1);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.getReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Отчёт ещё не сформирован - NotFoundException")
        void throwsWhenReportNotYetGenerated() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    UUID.randomUUID(), 1);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.getReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Report not found");
        }

        @Test
        @DisplayName("Отчёт сформирован - маппится только с отвеченными основными вопросами по orderIndex, уточнения в отчёт не идут")
        void returnsMappedReportWithAnsweredMainQuestionsOnly() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    UUID.randomUUID(), 2);
            InterviewQuestion answeredSecond = aQuestion(UUID.randomUUID(), null, 2, false, true, "Вопрос 2", "Ответ 2");
            InterviewQuestion answeredFirst = aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion answeredFollowUp = aQuestion(UUID.randomUUID(), answeredFirst.getId(), 1, true, true,
                    "Уточнение к вопросу 1", "Ответ на уточнение");
            InterviewQuestion unanswered = aQuestion(UUID.randomUUID(), null, 3, false, false, "Вопрос 3", null);
            session.setQuestions(List.of(answeredSecond, answeredFirst, answeredFollowUp, unanswered));
            InterviewReport report = InterviewReport.builder()
                    .id(UUID.randomUUID())
                    .avgScore(4.0)
                    .offerProbability(InterviewReport.OfferProbability.HIGH)
                    .overallFeedback("Хороший результат интервью")
                    .build();
            session.setReport(report);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewReportMapper.toResponse(report, session, List.of(answeredFirst, answeredSecond)))
                    .thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.getReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }
    }

}
