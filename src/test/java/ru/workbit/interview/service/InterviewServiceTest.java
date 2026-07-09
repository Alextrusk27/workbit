package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.InternalServerException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.CreateSessionByVacancyRequest;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.QuestionRequest;
import ru.workbit.interview.dto.QuestionResponse;
import ru.workbit.interview.dto.SessionReport;
import ru.workbit.interview.dto.SessionResponse;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.BankQuestion;
import ru.workbit.interview.model.Category;
import ru.workbit.interview.model.CompanyType;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.OfferProbability;
import ru.workbit.interview.model.Profession;
import ru.workbit.interview.model.SessionSource;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.mapper.QuestionMapper;
import ru.workbit.interview.model.mapper.SessionMapper;
import ru.workbit.interview.repository.QuestionBankRepository;
import ru.workbit.interview.repository.QuestionRepository;
import ru.workbit.interview.repository.SessionRepository;
import ru.workbit.llm.dto.LlmAnswerEvaluation;
import ru.workbit.llm.dto.LlmAnswerEvaluationRequest;
import ru.workbit.llm.dto.LlmAnswerReview;
import ru.workbit.llm.dto.LlmGeneratedQuestions;
import ru.workbit.llm.dto.LlmQuestionGenerationRequest;
import ru.workbit.llm.dto.LlmReport;
import ru.workbit.llm.dto.LlmReportRequest;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.service.VacancyService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewServiceTest")
class InterviewServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    QuestionBankRepository questionBankRepository;
    @Mock
    LlmService llmService;
    @Mock
    VacancyService vacancyService;
    @Mock
    VacancySessionCreator vacancySessionCreator;
    @Mock
    SessionMapper sessionMapper;
    @Mock
    QuestionMapper questionMapper;
    @Mock
    SessionRepository sessionRepository;
    @Mock
    QuestionRepository questionRepository;
    @Mock
    InterviewWriter interviewWriter;

    @InjectMocks
    InterviewService interviewService;

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
    // getOptions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Возвращает все значения enum'ов и границы MIN/MAX_QUESTIONS")
        void returnsEnumValuesAndQuestionBounds() {
            // when
            var result = interviewService.getOptions();

            // then
            assertThat(result.professions()).containsExactly(Profession.values());
            assertThat(result.levels()).containsExactly(Level.values());
            assertThat(result.companyTypes()).containsExactly(CompanyType.values());
            assertThat(result.minQuestions()).isEqualTo(CreateSessionRequest.MIN_QUESTIONS);
            assertThat(result.maxQuestions()).isEqualTo(CreateSessionRequest.MAX_QUESTIONS);
        }
    }

    // -------------------------------------------------------------------------
    // createSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        private final CreateSessionRequest request =
                new CreateSessionRequest(Profession.JAVA_DEV, Level.MIDDLE, CompanyType.PRODUCT, 2);

        @Test
        @DisplayName("Маппит запрос, устанавливает userId, генерирует вопросы и сохраняет сессию")
        void mapsSetsUserIdGeneratesQuestionsAndSaves() {
            // given
            InterviewSession session = aSessionBuilder().id(null).build();
            when(sessionMapper.toEntity(request)).thenReturn(session);

            List<BankQuestion> bankQuestions = List.of(
                    BankQuestion.builder()
                            .category(Category.JAVA_CORE).level(Level.MIDDLE).text("Что такое JVM?").build(),
                    BankQuestion.builder()
                            .category(Category.SPRING).level(Level.MIDDLE).text("Что такое Spring контекст?").build()
            );
            when(questionBankRepository.pickRandomByLevel(Level.MIDDLE, 2)).thenReturn(bankQuestions);
            when(questionMapper.toEntity(any(BankQuestion.class), eq(session)))
                    .thenAnswer(inv -> InterviewQuestion.builder()
                            .questionText(((BankQuestion) inv.getArgument(0)).getText())
                            .session(inv.getArgument(1))
                            .build());

            SessionResponse expectedResponse = new SessionResponse(
                    null, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    SessionSource.CATALOG, null,
                    SessionStatus.CREATED, 2, 0, null, null);
            when(sessionMapper.toResponse(session, 0, null)).thenReturn(expectedResponse);

            // when
            var result = interviewService.createSession(request, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(session.getUserId()).isEqualTo(USER_ID);
            assertThat(session.getQuestions()).hasSize(2);
            assertThat(session.getQuestions().get(0).getOrderIndex()).isEqualTo(1);
            assertThat(session.getQuestions().get(1).getOrderIndex()).isEqualTo(2);
            verify(sessionRepository).save(session);
        }

        @Test
        @DisplayName("Бросает InternalServerException, когда банк вопросов вернул пустой список")
        void throwsWhenQuestionBankReturnsEmptyList() {
            // given
            InterviewSession session = aSessionBuilder().id(null).build();
            when(sessionMapper.toEntity(request)).thenReturn(session);
            when(questionBankRepository.pickRandomByLevel(Level.MIDDLE, 2)).thenReturn(List.of());

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(request, USER_ID))
                    .isInstanceOf(InternalServerException.class)
                    .hasMessage("No questions found");

            verifyNoInteractions(questionMapper);
            verify(sessionRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // createSessionByVacancy
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CreateSessionByVacancy")
    class CreateSessionByVacancy {

        private VacancyData aVacancyData() {
            return new VacancyData(123L, "https://hh.ru/vacancy/123", "Java-разработчик",
                    "ООО Ромашка", "От 3 до 6 лет", List.of("Java", "Spring", "SQL"), "Описание вакансии");
        }

        private InterviewSession aPersistedSession(UUID vacancySnapshotId, int totalQuestions) {
            return InterviewSession.builder()
                    .id(SESSION_ID)
                    .userId(USER_ID)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .status(SessionStatus.CREATED)
                    .totalQuestions(totalQuestions)
                    .build();
        }

        @Test
        @DisplayName("Получает данные вакансии по URL, генерирует вопросы через LLM и сохраняет сессию")
        void fetchesByUrlPersistsAndMapsResponse() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 3);
            when(vacancyService.fetch("https://hh.ru/vacancy/123")).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Сгенерированный заголовок",
                    List.of("Q1", "Q2", "Q3"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);

            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession persistedSession = aPersistedSession(vacancySnapshotId, 3);
            when(vacancySessionCreator.persist(data, "Java-разработчик", List.of("Q1", "Q2", "Q3"), USER_ID))
                    .thenReturn(persistedSession);

            SessionResponse expectedResponse = new SessionResponse(
                    SESSION_ID, null, null, null,
                    SessionSource.VACANCY,
                    new SessionResponse.VacancyInfo("Java-разработчик", "ООО Ромашка", "https://hh.ru/vacancy/123", "От 3 до 6 лет"),
                    SessionStatus.CREATED, 3, 0, null, null);
            when(sessionMapper.toResponse(persistedSession, 0,
                    new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "https://hh.ru/vacancy/123", "От 3 до 6 лет")))
                    .thenReturn(expectedResponse);

            // when
            var result = interviewService.createSessionByVacancy(request, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(vacancyService).fetch("https://hh.ru/vacancy/123");
            verify(vacancyService, never()).fromText(any());
        }

        @Test
        @DisplayName("Получает данные вакансии из текста, когда URL не задан")
        void fromTextWhenUrlBlank() {
            // given
            VacancyData data = new VacancyData(null, null, null, null, null, null, "Текст вакансии, достаточно длинный");
            var request = new CreateSessionByVacancyRequest(null, "Текст вакансии, достаточно длинный", 2);
            when(vacancyService.fromText("Текст вакансии, достаточно длинный")).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок из LLM", List.of("Q1", "Q2"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);

            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession persistedSession = aPersistedSession(vacancySnapshotId, 2);
            when(vacancySessionCreator.persist(data, "Заголовок из LLM", List.of("Q1", "Q2"), USER_ID))
                    .thenReturn(persistedSession);

            SessionResponse expectedResponse = new SessionResponse(
                    SESSION_ID, null, null, null,
                    SessionSource.VACANCY,
                    new SessionResponse.VacancyInfo("Заголовок из LLM", null, null, null),
                    SessionStatus.CREATED, 2, 0, null, null);
            when(sessionMapper.toResponse(persistedSession, 0,
                    new VacancySnapshotView("Заголовок из LLM", null, null, null)))
                    .thenReturn(expectedResponse);

            // when
            var result = interviewService.createSessionByVacancy(request, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(vacancyService).fromText("Текст вакансии, достаточно длинный");
            verify(vacancyService, never()).fetch(any());
        }

        @Test
        @DisplayName("Прокидывает поля вакансии в запрос к LLM и склеивает keySkills через ', '")
        void passesVacancyFieldsToLlmRequest() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 3);
            when(vacancyService.fetch(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок", List.of("Q1", "Q2", "Q3"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 3));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(LlmQuestionGenerationRequest.class);
            verify(llmService).generateVacancyQuestions(captor.capture());
            assertThat(captor.getValue().vacancyName()).isEqualTo("Java-разработчик");
            assertThat(captor.getValue().employer()).isEqualTo("ООО Ромашка");
            assertThat(captor.getValue().experience()).isEqualTo("От 3 до 6 лет");
            assertThat(captor.getValue().keySkills()).isEqualTo("Java, Spring, SQL");
            assertThat(captor.getValue().description()).isEqualTo("Описание вакансии");
            assertThat(captor.getValue().questionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Заменяет null-поля вакансии на пустые строки, а null keySkills на пустую строку")
        void nullVacancyFieldsBecomeEmptyStringsInLlmRequest() {
            // given
            VacancyData data = new VacancyData(null, null, null, null, null, null, "Текст вакансии, достаточно длинный");
            var request = new CreateSessionByVacancyRequest(null, "Текст вакансии, достаточно длинный", 2);
            when(vacancyService.fromText(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок", List.of("Q1", "Q2"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 2));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(LlmQuestionGenerationRequest.class);
            verify(llmService).generateVacancyQuestions(captor.capture());
            assertThat(captor.getValue().vacancyName()).isEmpty();
            assertThat(captor.getValue().employer()).isEmpty();
            assertThat(captor.getValue().experience()).isEmpty();
            assertThat(captor.getValue().keySkills()).isEmpty();
        }

        @Test
        @DisplayName("Обрезает вопросы до totalQuestions, когда LLM сгенерировала больше")
        void trimsQuestionsWhenLlmReturnsMoreThanRequested() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 2);
            when(vacancyService.fetch(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок",
                    List.of("Q1", "Q2", "Q3", "Q4"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 2));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(List.class);
            verify(vacancySessionCreator).persist(eq(data), eq("Java-разработчик"), captor.capture(), eq(USER_ID));
            assertThat(captor.getValue()).containsExactly("Q1", "Q2");
        }

        @Test
        @DisplayName("Использует все вопросы, когда LLM сгенерировала меньше, чем запрошено")
        void keepsAllQuestionsWhenLlmReturnsFewerThanRequested() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 5);
            when(vacancyService.fetch(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок", List.of("Q1", "Q2"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 2));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(List.class);
            verify(vacancySessionCreator).persist(eq(data), eq("Java-разработчик"), captor.capture(), eq(USER_ID));
            assertThat(captor.getValue()).containsExactly("Q1", "Q2");
        }

        @Test
        @DisplayName("Бросает LlmException и не сохраняет сессию, когда LLM вернула пустой список вопросов")
        void throwsLlmExceptionWhenGeneratedQuestionsEmpty() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 3);
            when(vacancyService.fetch(any())).thenReturn(data);
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class)))
                    .thenReturn(new LlmGeneratedQuestions("Заголовок", List.of()));

            // when / then
            assertThatThrownBy(() -> interviewService.createSessionByVacancy(request, USER_ID))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Question generator returned no questions");

            verifyNoInteractions(vacancySessionCreator, sessionMapper);
        }

        @Test
        @DisplayName("Бросает LlmException и не сохраняет сессию, когда LLM вернула null вместо списка вопросов")
        void throwsLlmExceptionWhenGeneratedQuestionsNull() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 3);
            when(vacancyService.fetch(any())).thenReturn(data);
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class)))
                    .thenReturn(new LlmGeneratedQuestions("Заголовок", null));

            // when / then
            assertThatThrownBy(() -> interviewService.createSessionByVacancy(request, USER_ID))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Question generator returned no questions");

            verifyNoInteractions(vacancySessionCreator, sessionMapper);
        }

        @Test
        @DisplayName("Берёт имя из заголовка LLM, когда имя вакансии не задано")
        void resolvesNameFromGeneratedTitleWhenVacancyNameBlank() {
            // given
            VacancyData data = new VacancyData(null, null, "  ", null, null, null, "Текст вакансии, достаточно длинный");
            var request = new CreateSessionByVacancyRequest(null, "Текст вакансии, достаточно длинный", 1);
            when(vacancyService.fromText(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок из LLM", List.of("Q1"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 1));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            verify(vacancySessionCreator).persist(eq(data), eq("Заголовок из LLM"), any(), eq(USER_ID));
        }

        @Test
        @DisplayName("Использует фолбэк 'Вакансия', когда ни имя вакансии, ни заголовок LLM не заданы")
        void resolvesNameFallbackWhenBothBlank() {
            // given
            VacancyData data = new VacancyData(null, null, null, null, null, null, "Текст вакансии, достаточно длинный");
            var request = new CreateSessionByVacancyRequest(null, "Текст вакансии, достаточно длинный", 1);
            when(vacancyService.fromText(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions(null, List.of("Q1"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 1));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            verify(vacancySessionCreator).persist(eq(data), eq("Вакансия"), any(), eq(USER_ID));
        }

        @Test
        @DisplayName("Отфильтровывает null и пустые вопросы от LLM перед сохранением")
        void filtersOutNullAndBlankQuestionsBeforePersisting() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 5);
            when(vacancyService.fetch(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("Заголовок",
                    Arrays.asList("Q1", null, "   ", "Q2"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 2));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(List.class);
            verify(vacancySessionCreator).persist(eq(data), any(), captor.capture(), eq(USER_ID));
            assertThat(captor.getValue()).containsExactly("Q1", "Q2");
        }

        @Test
        @DisplayName("Бросает LlmException, когда все сгенерированные вопросы пустые/null")
        void throwsLlmExceptionWhenAllGeneratedQuestionsBlank() {
            // given
            VacancyData data = aVacancyData();
            var request = new CreateSessionByVacancyRequest("https://hh.ru/vacancy/123", null, 3);
            when(vacancyService.fetch(any())).thenReturn(data);
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class)))
                    .thenReturn(new LlmGeneratedQuestions("Заголовок", Arrays.asList(null, "  ", "")));

            // when / then
            assertThatThrownBy(() -> interviewService.createSessionByVacancy(request, USER_ID))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Question generator returned no questions");

            verifyNoInteractions(vacancySessionCreator, sessionMapper);
        }

        @Test
        @DisplayName("Обрезает слишком длинное имя вакансии до 255 символов")
        void clampsTooLongVacancyNameTo255Characters() {
            // given
            VacancyData data = new VacancyData(null, null, null, null, null, null, "Текст вакансии, достаточно длинный");
            var request = new CreateSessionByVacancyRequest(null, "Текст вакансии, достаточно длинный", 1);
            when(vacancyService.fromText(any())).thenReturn(data);

            LlmGeneratedQuestions generated = new LlmGeneratedQuestions("a".repeat(300), List.of("Q1"));
            when(llmService.generateVacancyQuestions(any(LlmQuestionGenerationRequest.class))).thenReturn(generated);
            when(vacancySessionCreator.persist(any(), any(), any(), any()))
                    .thenReturn(aPersistedSession(UUID.randomUUID(), 1));
            when(sessionMapper.toResponse(any(), eq(0), any())).thenReturn(mock(SessionResponse.class));

            // when
            interviewService.createSessionByVacancy(request, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(String.class);
            verify(vacancySessionCreator).persist(eq(data), captor.capture(), any(), eq(USER_ID));
            assertThat(captor.getValue()).hasSize(255);
        }
    }

    // -------------------------------------------------------------------------
    // getAllSessions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetAllSessions")
    class GetAllSessions {

        @Test
        @DisplayName("Собирает карту количества ответов и подставляет 0 для сессий без ответов")
        void buildsAnsweredCountMapWithDefaultZero() {
            // given
            UUID session1Id = UUID.randomUUID();
            UUID session2Id = UUID.randomUUID();
            InterviewSession session1 = aSessionBuilder().id(session1Id).build();
            InterviewSession session2 = aSessionBuilder().id(session2Id).build();
            when(sessionRepository.findAllByUserId(USER_ID)).thenReturn(List.of(session1, session2));

            QuestionRepository.AnsweredCount answeredCount1 = mock(QuestionRepository.AnsweredCount.class);
            when(answeredCount1.getSessionId()).thenReturn(session1Id);
            when(answeredCount1.getCount()).thenReturn(3L);
            when(questionRepository.countAnsweredBySessionIds(List.of(session1Id, session2Id)))
                    .thenReturn(List.of(answeredCount1));

            when(vacancyService.getSnapshotViews(any())).thenReturn(Map.of());

            SessionResponse response1 = new SessionResponse(
                    session1Id, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    SessionSource.CATALOG, null,
                    SessionStatus.CREATED, 10, 3, null, null);
            SessionResponse response2 = new SessionResponse(
                    session2Id, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    SessionSource.CATALOG, null,
                    SessionStatus.CREATED, 10, 0, null, null);
            when(sessionMapper.toResponse(session1, 3, null)).thenReturn(response1);
            when(sessionMapper.toResponse(session2, 0, null)).thenReturn(response2);

            // when
            var result = interviewService.getAllSessions(USER_ID);

            // then
            assertThat(result).containsExactly(response1, response2);
        }

        @Test
        @DisplayName("Подставляет данные вакансии для VACANCY-сессий из карты снапшотов")
        void resolvesVacancyInfoForVacancySessions() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession catalogSession = aSessionBuilder().id(SESSION_ID).build();
            InterviewSession vacancySession = aSessionBuilder()
                    .id(UUID.randomUUID())
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .build();
            when(sessionRepository.findAllByUserId(USER_ID)).thenReturn(List.of(catalogSession, vacancySession));
            when(questionRepository.countAnsweredBySessionIds(
                    List.of(catalogSession.getId(), vacancySession.getId())))
                    .thenReturn(List.of());

            VacancySnapshotView vacancyView = new VacancySnapshotView(
                    "Java-разработчик", "ООО Ромашка", "url", "От 3 до 6 лет");
            when(vacancyService.getSnapshotViews(List.of(vacancySnapshotId)))
                    .thenReturn(Map.of(vacancySnapshotId, vacancyView));

            SessionResponse catalogResponse = mock(SessionResponse.class);
            SessionResponse vacancyResponse = mock(SessionResponse.class);
            when(sessionMapper.toResponse(catalogSession, 0, null)).thenReturn(catalogResponse);
            when(sessionMapper.toResponse(vacancySession, 0, vacancyView)).thenReturn(vacancyResponse);

            // when
            var result = interviewService.getAllSessions(USER_ID);

            // then
            assertThat(result).containsExactly(catalogResponse, vacancyResponse);
        }
    }

    // -------------------------------------------------------------------------
    // getSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetSession")
    class GetSession {

        @Test
        @DisplayName("Возвращает сессию с количеством отвеченных вопросов")
        void returnsSessionWithAnsweredCount() {
            // given
            InterviewSession session = aSessionBuilder().build();
            when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
            when(questionRepository.countBySessionIdAndAnsweredTrue(SESSION_ID)).thenReturn(3L);

            SessionResponse expectedResponse = new SessionResponse(
                    SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    SessionSource.CATALOG, null,
                    SessionStatus.CREATED, 10, 3, null, null);
            when(sessionMapper.toResponse(session, 3, null)).thenReturn(expectedResponse);

            // when
            var result = interviewService.getSession(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verifyNoInteractions(vacancyService);
        }

        @Test
        @DisplayName("Подставляет данные вакансии из снапшота для VACANCY-сессии")
        void resolvesVacancyInfoForVacancySession() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSessionBuilder()
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .build();
            when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
            when(questionRepository.countBySessionIdAndAnsweredTrue(SESSION_ID)).thenReturn(0L);

            VacancySnapshotView vacancyView = new VacancySnapshotView(
                    "Java-разработчик", "ООО Ромашка", "url", "От 3 до 6 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancyView);

            SessionResponse expectedResponse = mock(SessionResponse.class);
            when(sessionMapper.toResponse(session, 0, vacancyView)).thenReturn(expectedResponse);

            // when
            var result = interviewService.getSession(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда сессия не найдена")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.getSession(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(questionRepository, sessionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // continueSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ContinueSession")
    class ContinueSession {

        @Test
        @DisplayName("Возвращает следующий неотвеченный вопрос")
        void returnsNextUnansweredQuestion() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
            InterviewQuestion question = InterviewQuestion.builder().id(UUID.randomUUID()).build();
            when(questionRepository.findNextUnanswered(SESSION_ID)).thenReturn(Optional.of(question));

            QuestionResponse expectedResponse = new QuestionResponse(
                    question.getId(), 1, "Вопрос?", null, null, null);
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            // when
            var result = interviewService.continueSession(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда у пользователя нет такой сессии")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> interviewService.continueSession(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(questionRepository, questionMapper);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда неотвеченных вопросов не осталось")
        void throwsWhenNoUnansweredQuestionsLeft() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
            when(questionRepository.findNextUnanswered(SESSION_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.continueSession(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("This session finished");

            verifyNoInteractions(questionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // getQuestion
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetQuestion")
    class GetQuestion {

        private final QuestionRequest request = new QuestionRequest(SESSION_ID, 1, USER_ID);

        @Test
        @DisplayName("Возвращает вопрос по индексу")
        void returnsQuestionByIndex() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
            InterviewQuestion question = InterviewQuestion.builder().id(UUID.randomUUID()).build();
            when(questionRepository.findBySessionIdAndOrderIndex(SESSION_ID, 1)).thenReturn(Optional.of(question));

            QuestionResponse expectedResponse = new QuestionResponse(
                    question.getId(), 1, "Вопрос?", null, null, null);
            when(questionMapper.toDto(question)).thenReturn(expectedResponse);

            // when
            var result = interviewService.getQuestion(request);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда у пользователя нет такой сессии")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> interviewService.getQuestion(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(questionRepository, questionMapper);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда вопрос с таким индексом не найден")
        void throwsWhenQuestionNotFound() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);
            when(questionRepository.findBySessionIdAndOrderIndex(SESSION_ID, 1)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.getQuestion(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");

            verifyNoInteractions(questionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // submitAnswer
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SubmitAnswer")
    class SubmitAnswer {

        private final UUID questionId = UUID.randomUUID();

        private QuestionResponse aQuestionResponse() {
            return new QuestionResponse(questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", null, null);
        }

        @Test
        @DisplayName("Оценивает ответ через LLM и возвращает результат interviewWriter.saveFeedback, когда evaluate=true")
        void evaluatesAnswerAndReturnsSavedFeedbackWhenEvaluateTrue() {
            // given
            InterviewSession session = aSessionBuilder().build();
            InterviewWriter.AnswerContext context =
                    new InterviewWriter.AnswerContext(aQuestionResponse(), session, "Что такое JVM?");
            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "JVM - виртуальная машина");
            when(interviewWriter.saveAnswer(request)).thenReturn(context);

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "Хороший ответ");
            when(llmService.evaluateAnswer(any(LlmAnswerEvaluationRequest.class))).thenReturn(evaluation);

            QuestionResponse expectedResponse = new QuestionResponse(
                    questionId, 1, "Что такое JVM?", "JVM - виртуальная машина", 4, "Хороший ответ");
            when(interviewWriter.saveFeedback(questionId, evaluation)).thenReturn(expectedResponse);

            // when
            var result = interviewService.submitAnswer(request);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            var evalRequestCaptor = ArgumentCaptor.forClass(LlmAnswerEvaluationRequest.class);
            verify(llmService).evaluateAnswer(evalRequestCaptor.capture());
            assertThat(evalRequestCaptor.getValue().profession()).isEqualTo("Java-разработчик");
            assertThat(evalRequestCaptor.getValue().question()).isEqualTo("Что такое JVM?");
            assertThat(evalRequestCaptor.getValue().level()).isEqualTo("Middle");
            assertThat(evalRequestCaptor.getValue().answer()).isEqualTo("JVM - виртуальная машина");

            verify(interviewWriter).saveFeedback(questionId, evaluation);
        }

        @Test
        @DisplayName("Не вызывает оценку LLM и возвращает ответ из контекста, когда evaluate=false")
        void doesNotEvaluateWhenEvaluateFalse() {
            // given
            InterviewSession session = aSessionBuilder().build();
            QuestionResponse contextResponse = aQuestionResponse();
            InterviewWriter.AnswerContext context =
                    new InterviewWriter.AnswerContext(contextResponse, session, "Что такое JVM?");
            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, false, "JVM - виртуальная машина");
            when(interviewWriter.saveAnswer(request)).thenReturn(context);

            // when
            var result = interviewService.submitAnswer(request);

            // then
            assertThat(result).isEqualTo(contextResponse);
            verifyNoInteractions(llmService);
            verify(interviewWriter, never()).saveFeedback(any(), any());
        }

        @Test
        @DisplayName("Берёт профессию и опыт из снапшота вакансии для VACANCY-сессии")
        void usesVacancySnapshotContextForVacancySession() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSessionBuilder()
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .build();
            InterviewWriter.AnswerContext context =
                    new InterviewWriter.AnswerContext(aQuestionResponse(), session, "Что такое JVM?");
            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "ответ");
            when(interviewWriter.saveAnswer(request)).thenReturn(context);

            when(vacancyService.getSnapshotView(vacancySnapshotId))
                    .thenReturn(new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "url", "От 3 до 6 лет"));

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "Неплохо");
            when(llmService.evaluateAnswer(any(LlmAnswerEvaluationRequest.class))).thenReturn(evaluation);

            QuestionResponse expectedResponse = aQuestionResponse();
            when(interviewWriter.saveFeedback(questionId, evaluation)).thenReturn(expectedResponse);

            // when
            var result = interviewService.submitAnswer(request);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            var captor = ArgumentCaptor.forClass(LlmAnswerEvaluationRequest.class);
            verify(llmService).evaluateAnswer(captor.capture());
            assertThat(captor.getValue().profession()).isEqualTo("Java-разработчик");
            assertThat(captor.getValue().level()).isEqualTo("От 3 до 6 лет");
        }

        @Test
        @DisplayName("Подставляет 'не указан', когда опыт в снапшоте вакансии не задан")
        void usesNotSpecifiedLabelWhenSnapshotExperienceBlank() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSessionBuilder()
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .build();
            InterviewWriter.AnswerContext context =
                    new InterviewWriter.AnswerContext(aQuestionResponse(), session, "Что такое JVM?");
            var request = new SubmitAnswerRequest(USER_ID, SESSION_ID, questionId, true, "ответ");
            when(interviewWriter.saveAnswer(request)).thenReturn(context);

            when(vacancyService.getSnapshotView(vacancySnapshotId))
                    .thenReturn(new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "url", null));

            LlmAnswerEvaluation evaluation = new LlmAnswerEvaluation(4, "Неплохо");
            when(llmService.evaluateAnswer(any(LlmAnswerEvaluationRequest.class))).thenReturn(evaluation);
            when(interviewWriter.saveFeedback(eq(questionId), eq(evaluation))).thenReturn(aQuestionResponse());

            // when
            interviewService.submitAnswer(request);

            // then
            var captor = ArgumentCaptor.forClass(LlmAnswerEvaluationRequest.class);
            verify(llmService).evaluateAnswer(captor.capture());
            assertThat(captor.getValue().level()).isEqualTo("не указан");
        }
    }

    // -------------------------------------------------------------------------
    // finishSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FinishSession")
    class FinishSession {

        @Test
        @DisplayName("Бросает NotFoundException, когда сессия не найдена")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.finishSession(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(llmService, interviewWriter);
        }

        @Test
        @DisplayName("Бросает ForbiddenException, когда сессия принадлежит другому пользователю")
        void throwsWhenSessionOwnedByAnotherUser() {
            // given
            InterviewSession session = aSessionBuilder().userId(UUID.randomUUID()).build();
            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.finishSession(SESSION_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");

            verifyNoInteractions(llmService, interviewWriter);
        }

        @Test
        @DisplayName("Бросает ConflictException и не зовёт LLM, когда сессия уже завершена")
        void throwsWhenSessionAlreadyCompleted() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.COMPLETED).build();
            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.finishSession(SESSION_ID, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");

            verifyNoInteractions(llmService, interviewWriter);
        }

        @Test
        @DisplayName("Строит отчёт через LLM и делегирует его сборку и сохранение interviewWriter.completeReport")
        void delegatesReportCompletionToInterviewWriter() {
            // given
            InterviewSession session = aSessionBuilder().status(SessionStatus.IN_PROGRESS).build();
            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            session.setQuestions(List.of(q1));
            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));

            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)), "Отчёт", "MEDIUM");
            when(llmService.createReport(any(LlmReportRequest.class))).thenReturn(llmReport);

            SessionReport expectedReport = new SessionReport(
                    UUID.randomUUID(), SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 4.0, "Отчёт", OfferProbability.MEDIUM, null);
            when(interviewWriter.completeReport(SESSION_ID, llmReport)).thenReturn(expectedReport);

            // when
            var result = interviewService.finishSession(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedReport);
            verify(interviewWriter).completeReport(SESSION_ID, llmReport);
        }

        @Test
        @DisplayName("Берёт контекст отчёта из снапшота вакансии для VACANCY-сессии")
        void usesVacancySnapshotContextForVacancySession() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSessionBuilder()
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .status(SessionStatus.IN_PROGRESS)
                    .build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));
            when(vacancyService.getSnapshotView(vacancySnapshotId))
                    .thenReturn(new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "url", "От 3 до 6 лет"));

            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)), "Отчёт", "HIGH");
            when(llmService.createReport(any(LlmReportRequest.class))).thenReturn(llmReport);

            SessionReport expectedReport = mock(SessionReport.class);
            when(interviewWriter.completeReport(SESSION_ID, llmReport)).thenReturn(expectedReport);

            // when
            var result = interviewService.finishSession(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedReport);
            var captor = ArgumentCaptor.forClass(LlmReportRequest.class);
            verify(llmService).createReport(captor.capture());
            assertThat(captor.getValue().profession()).isEqualTo("Java-разработчик");
            assertThat(captor.getValue().level()).isEqualTo("От 3 до 6 лет");
        }

        @Test
        @DisplayName("Подставляет 'не указан' в отчёт, когда опыт в снапшоте вакансии не задан")
        void usesNotSpecifiedLabelWhenSnapshotExperienceBlank() {
            // given
            UUID vacancySnapshotId = UUID.randomUUID();
            InterviewSession session = aSessionBuilder()
                    .profession(null).level(null)
                    .source(SessionSource.VACANCY)
                    .vacancySnapshotId(vacancySnapshotId)
                    .status(SessionStatus.IN_PROGRESS)
                    .build();

            UUID q1Id = UUID.randomUUID();
            InterviewQuestion q1 = InterviewQuestion.builder()
                    .id(q1Id).session(session).questionText("Q1").answerText("A1").build();
            session.setQuestions(List.of(q1));

            when(sessionRepository.findWithQuestionsById(SESSION_ID)).thenReturn(Optional.of(session));
            when(vacancyService.getSnapshotView(vacancySnapshotId))
                    .thenReturn(new VacancySnapshotView("Java-разработчик", "ООО Ромашка", "url", ""));

            LlmReport llmReport = new LlmReport(
                    List.of(new LlmAnswerReview(q1Id, "Хорошо", 4)), "Отчёт", "HIGH");
            when(llmService.createReport(any(LlmReportRequest.class))).thenReturn(llmReport);

            SessionReport expectedReport = mock(SessionReport.class);
            when(interviewWriter.completeReport(SESSION_ID, llmReport)).thenReturn(expectedReport);

            // when
            interviewService.finishSession(SESSION_ID, USER_ID);

            // then
            var captor = ArgumentCaptor.forClass(LlmReportRequest.class);
            verify(llmService).createReport(captor.capture());
            assertThat(captor.getValue().level()).isEqualTo("не указан");
        }
    }

    // -------------------------------------------------------------------------
    // getSessionReport
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GetSessionReport")
    class GetSessionReport {

        @Test
        @DisplayName("Возвращает отчёт по сессии")
        void returnsSessionReport() {
            // given
            InterviewSession session = aSessionBuilder().build();
            when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));

            SessionReport expectedReport = new SessionReport(
                    UUID.randomUUID(), SESSION_ID, Profession.JAVA_DEV, CompanyType.PRODUCT, Level.MIDDLE,
                    10, 7.5, "Отчёт", OfferProbability.HIGH, null);
            when(sessionMapper.toSessionReport(session)).thenReturn(expectedReport);

            // when
            var result = interviewService.getSessionReport(SESSION_ID, USER_ID);

            // then
            assertThat(result).isEqualTo(expectedReport);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда сессия не найдена")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.getSessionReport(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verifyNoInteractions(sessionMapper);
        }
    }

    // -------------------------------------------------------------------------
    // deleteSession
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DeleteSession")
    class DeleteSession {

        @Test
        @DisplayName("Удаляет сессию по id")
        void deletesSessionById() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(true);

            // when
            interviewService.deleteSession(SESSION_ID, USER_ID);

            // then
            verify(sessionRepository).deleteById(SESSION_ID);
        }

        @Test
        @DisplayName("Бросает NotFoundException, когда у пользователя нет такой сессии")
        void throwsWhenSessionNotFound() {
            // given
            when(sessionRepository.existsByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> interviewService.deleteSession(SESSION_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");

            verify(sessionRepository, never()).deleteById(any());
        }
    }
}
