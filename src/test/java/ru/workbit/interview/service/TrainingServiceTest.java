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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.TopicDict;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.QuestionBankRepository;
import ru.workbit.content.repository.TopicDictRepository;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.NormalizeInputRequest;
import ru.workbit.interview.dto.NormalizeInputResponse;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.dto.TrainingOptionsResponse;
import ru.workbit.interview.dto.TrainingQuestionResponse;
import ru.workbit.interview.dto.TrainingReportResponse;
import ru.workbit.interview.dto.TrainingSessionResponse;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingReport;
import ru.workbit.interview.model.TrainingSession;
import ru.workbit.interview.model.mapper.TrainingQuestionMapper;
import ru.workbit.interview.model.mapper.TrainingReportMapper;
import ru.workbit.interview.model.mapper.TrainingSessionMapper;
import ru.workbit.interview.repository.TrainingQuestionRepository;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmTrainingFollowUp;
import ru.workbit.llm.dto.LlmTrainingFollowUpDecision;
import ru.workbit.llm.dto.LlmTrainingFollowUpRequest;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.llm.service.LlmService;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrainingServiceTest")
class TrainingServiceTest {

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
    QuestionBankRepository questionBankRepository;
    @Mock
    TrainingWriter trainingWriter;
    @Mock
    LlmService llmService;
    @Mock
    TrainingSessionMapper trainingSessionMapper;
    @Mock
    TrainingQuestionMapper trainingQuestionMapper;
    @Mock
    TrainingReportMapper trainingReportMapper;

    @InjectMocks
    TrainingService trainingService;

    private TrainingSession aSession(UUID sessionId, UUID userId, String profession) {
        return TrainingSession.builder()
                .id(sessionId)
                .userId(userId)
                .profession(profession)
                .level(Level.MIDDLE)
                .status(SessionStatus.CREATED)
                .build();
    }

    private TrainingQuestion aQuestion(int orderIndex) {
        return TrainingQuestion.builder()
                .id(UUID.randomUUID())
                .questionText("Вопрос " + orderIndex)
                .answerText("Ответ " + orderIndex)
                .orderIndex(orderIndex)
                .followUp(false)
                .answered(true)
                .build();
    }

    private static List<BankQuestion> bankQuestions(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> BankQuestion.builder().id(UUID.randomUUID()).text("Банковский вопрос " + i).build())
                .toList();
    }

    @Nested
    @DisplayName("Create")
    class Create {

        private final UUID userId = UUID.randomUUID();
        private final UUID professionId = UUID.randomUUID();
        private final UUID topicId = UUID.randomUUID();

        private TrainingSession mappedEntity(String topic) {
            return TrainingSession.builder()
                    .profession(PROFESSION)
                    .topic(topic)
                    .level(Level.MIDDLE)
                    .build();
        }

        @Test
        @DisplayName("Банк выдал полные 10 вопросов - LLM не вызывается, в writer уходят 10 банковских и пустой список сгенерированных")
        void fullBankSkipsLlmGeneration() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(TrainingService.MAIN_QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getUserId()).isEqualTo(userId);

            verifyNoInteractions(llmService);
            verify(trainingWriter).createSession(mappedEntity, bank, List.of());
        }

        @Test
        @DisplayName("Банк выдал часть вопросов (7 из 10) - LLM вызывается с count=3 и текстами банковских вопросов")
        void partialBankRequestsMissingFromLlm() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(7);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Сгенерированный 1", "Сгенерированный 2", "Сгенерированный 3");
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            verify(questionBankRepository).sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(captor.capture());
            LlmTrainingQuestionsRequest llmRequest = captor.getValue();
            assertThat(llmRequest.profession()).isEqualTo(PROFESSION);
            assertThat(llmRequest.topic()).isEqualTo(TOPIC);
            assertThat(llmRequest.level()).isEqualTo("Middle");
            assertThat(llmRequest.count()).isEqualTo(3);
            assertThat(llmRequest.existingQuestions())
                    .containsExactlyElementsOf(bank.stream().map(BankQuestion::getText).toList());

            verify(trainingWriter).createSession(mappedEntity, bank, generated);
        }

        @Test
        @DisplayName("Банк пуст - LLM запрашивается на полный батч из 10 вопросов")
        void emptyBankRequestsFullBatch() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(List.of());

            List<String> generated = IntStream.rangeClosed(1, TrainingService.MAIN_QUESTION_CAP)
                    .mapToObj(i -> "Сгенерированный " + i)
                    .toList();
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, List.of(), generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(captor.capture());
            assertThat(captor.getValue().count()).isEqualTo(TrainingService.MAIN_QUESTION_CAP);
            assertThat(captor.getValue().existingQuestions()).isEmpty();

            verify(trainingWriter).createSession(mappedEntity, List.of(), generated);
        }

        @Test
        @DisplayName("Ответ LLM с null/blank и лишними строками - фильтруется и обрезается до missing")
        void filtersAndTrimsLlmResponse() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(8);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);

            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(
                    Arrays.asList(null, "   ", "Годный вопрос 1", "Годный вопрос 2", "Лишний вопрос 3")));

            List<String> expectedGenerated = List.of("Годный вопрос 1", "Годный вопрос 2");
            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, expectedGenerated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).createSession(mappedEntity, bank, expectedGenerated);
        }

        @Test
        @DisplayName("LLM вернул меньше вопросов, чем запрошено - сессия создаётся с тем, что есть, без исключения")
        void createsSessionWithFewerThanRequestedFromLlm() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(8);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Единственный сгенерированный");
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).createSession(mappedEntity, bank, generated);
        }

        @Test
        @DisplayName("Банк пуст и LLM вернул null-список - LlmException (недостаточно вопросов), сессия не создаётся")
        void throwsWhenBankEmptyAndLlmReturnsNullList() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(null));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for a training session");
            verify(trainingWriter, never()).createSession(any(), any(), any());
        }

        @Test
        @DisplayName("Банк пуст и LLM вернул только blank-строки - LlmException (недостаточно вопросов), сессия не создаётся")
        void throwsWhenBankEmptyAndLlmReturnsOnlyBlankStrings() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(any()))
                    .thenReturn(new LlmTrainingQuestions(Arrays.asList("", "   ", null)));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for a training session");
            verify(trainingWriter, never()).createSession(any(), any(), any());
        }

        @Test
        @DisplayName("Суммарно 2 вопроса (2 из банка, LLM вернул 0) - LlmException, сессия не создаётся")
        void throwsWhenTotalQuestionsBelowThresholdFromBank() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(2);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(List.of()));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for a training session");
            verify(trainingWriter, never()).createSession(any(), any(), any());
        }

        @Test
        @DisplayName("Суммарно 2 вопроса (1 из банка, 1 от LLM) - LlmException, сессия не создаётся")
        void throwsWhenTotalQuestionsBelowThresholdMixedSources() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(1);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(any()))
                    .thenReturn(new LlmTrainingQuestions(List.of("Единственный сгенерированный")));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for a training session");
            verify(trainingWriter, never()).createSession(any(), any(), any());
        }

        @Test
        @DisplayName("Суммарно ровно 3 вопроса (порог не строгий) - сессия создаётся")
        void createsSessionWhenExactlyAtThreshold() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(TOPIC);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, TOPIC))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, topicId));

            List<BankQuestion> bank = bankQuestions(3);
            when(questionBankRepository.sampleUnseen(
                    professionId, topicId, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(List.of()));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).createSession(mappedEntity, bank, List.of());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   "})
        @DisplayName("Тема null или из пробелов - в upsertDictionaries уходит null, в LLM-запрос (при генерации) - пустая строка")
        void nullOrBlankTopicNormalizesForDictionariesAndLlm(String rawTopic) {
            // given
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, rawTopic, Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(rawTopic);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(trainingWriter.upsertDictionaries(PROFESSION, null))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, null));

            List<BankQuestion> bank = bankQuestions(9);
            when(questionBankRepository.sampleUnseen(
                    professionId, null, "MIDDLE", userId, TrainingService.MAIN_QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Доп. вопрос");
            when(llmService.generateTrainingQuestions(any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, null, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, generated)).thenReturn(expectedResponse);

            // when
            trainingService.create(request, userId);

            // then
            assertThat(mappedEntity.getTopic()).isNull();
            verify(trainingWriter).upsertDictionaries(PROFESSION, null);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(captor.capture());
            assertThat(captor.getValue().topic()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get")
    class Get {

        @Test
        @DisplayName("Сессия найдена - возвращает ответ с числом отвеченных основных вопросов")
        void returnsResponseWithAnsweredMainCount() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(sessionId))
                    .thenReturn(3L);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    sessionId, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 3, null, null);
            when(trainingSessionMapper.toResponse(session, 3)).thenReturn(expectedResponse);

            // when
            TrainingSessionResponse result = trainingService.get(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.get(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(trainingQuestionRepository, trainingSessionMapper);
        }
    }

    @Nested
    @DisplayName("GetAll")
    class GetAll {

        @Test
        @DisplayName("Мапит счётчик отвеченных вопросов по каждой сессии, для сессий без записи в счётчике - 0")
        void mapsAnsweredCountsPerSessionDefaultingToZero() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionWithAnswers = UUID.randomUUID();
            UUID sessionWithoutAnswers = UUID.randomUUID();
            TrainingSession first = aSession(sessionWithAnswers, userId, PROFESSION);
            TrainingSession second = aSession(sessionWithoutAnswers, userId, PROFESSION);
            Pageable pageable = Pageable.ofSize(10);
            Page<TrainingSession> page = new PageImpl<>(List.of(first, second));
            when(trainingSessionRepository.findAllByUserId(userId, pageable)).thenReturn(page);

            TrainingQuestionRepository.AnsweredCount answeredCount = mock(TrainingQuestionRepository.AnsweredCount.class);
            when(answeredCount.getSessionId()).thenReturn(sessionWithAnswers);
            when(answeredCount.getCount()).thenReturn(5L);
            when(trainingQuestionRepository.countAnsweredBySessionIds(List.of(sessionWithAnswers, sessionWithoutAnswers)))
                    .thenReturn(List.of(answeredCount));

            TrainingSessionResponse firstResponse = new TrainingSessionResponse(
                    sessionWithAnswers, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 5, null, null);
            TrainingSessionResponse secondResponse = new TrainingSessionResponse(
                    sessionWithoutAnswers, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(first, 5)).thenReturn(firstResponse);
            when(trainingSessionMapper.toResponse(second, 0)).thenReturn(secondResponse);

            // when
            Page<TrainingSessionResponse> result = trainingService.getAll(userId, pageable);

            // then
            assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        }
    }

    @Nested
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Профессии из словаря маппятся в имена с сохранением порядка, levels и капы как заданы")
        void returnsProfessionNamesInOrderWithLevelsAndCaps() {
            // given
            ProfessionDict first = ProfessionDict.builder().id(UUID.randomUUID()).name("Java-разработчик").build();
            ProfessionDict second = ProfessionDict.builder().id(UUID.randomUUID()).name("Python-разработчик").build();
            when(professionDictRepository.findTop20ByOrderByUsageCountDesc()).thenReturn(List.of(first, second));

            // when
            TrainingOptionsResponse result = trainingService.getOptions();

            // then
            assertThat(result.professions()).containsExactly("Java-разработчик", "Python-разработчик");
            assertThat(result.levels()).containsExactly(Level.values());
            assertThat(result.questionCap()).isEqualTo(TrainingService.MAIN_QUESTION_CAP);
            assertThat(result.minAnswersToFinish()).isEqualTo(TrainingService.MIN_ANSWERED_TO_FINISH);
        }

        @Test
        @DisplayName("Пустой словарь профессий - пустой список professions, а не ошибка")
        void returnsEmptyProfessionsWhenDictionaryEmpty() {
            // given
            when(professionDictRepository.findTop20ByOrderByUsageCountDesc()).thenReturn(List.of());

            // when
            TrainingOptionsResponse result = trainingService.getOptions();

            // then
            assertThat(result.professions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("NextQuestion")
    class NextQuestion {

        private TrainingQuestion answeredMain(UUID id, int orderIndex) {
            return TrainingQuestion.builder()
                    .id(id)
                    .questionText("Вопрос " + orderIndex)
                    .answerText("Ответ " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(false)
                    .answered(true)
                    .build();
        }

        private TrainingQuestion unansweredMain(UUID id, int orderIndex) {
            return TrainingQuestion.builder()
                    .id(id)
                    .questionText("Вопрос " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(false)
                    .answered(false)
                    .build();
        }

        private TrainingQuestion answeredFollowUp(UUID id, UUID parentQuestionId, int orderIndex) {
            return TrainingQuestion.builder()
                    .id(id)
                    .parentQuestionId(parentQuestionId)
                    .questionText("Уточнение " + orderIndex)
                    .answerText("Ответ на уточнение " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(true)
                    .answered(true)
                    .build();
        }

        private TrainingQuestion unansweredFollowUp(UUID id, UUID parentQuestionId, int orderIndex) {
            return TrainingQuestion.builder()
                    .id(id)
                    .parentQuestionId(parentQuestionId)
                    .questionText("Уточнение " + orderIndex)
                    .orderIndex(orderIndex)
                    .followUp(true)
                    .answered(false)
                    .build();
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(trainingQuestionRepository, llmService, trainingWriter);
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(SessionStatus.COMPLETED);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(trainingQuestionRepository, llmService, trainingWriter);
        }

        @Test
        @DisplayName("Есть неотвеченный follow-up - возвращает его напрямую, LLM не вызывается")
        void returnsPendingFollowUpWithoutCallingLlm() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            TrainingQuestion pendingFollowUp = unansweredFollowUp(UUID.randomUUID(), UUID.randomUUID(), 2);
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId))
                    .thenReturn(Optional.of(pendingFollowUp));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    pendingFollowUp.getId(), 2, pendingFollowUp.getQuestionText(), true, null, null, null);
            when(trainingQuestionMapper.toDto(pendingFollowUp)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verifyNoInteractions(llmService, trainingWriter);
            verify(trainingQuestionRepository, never()).findLastAnsweredWithoutFollowUpCheck(any());
        }

        @Test
        @DisplayName("Нет неотвеченного follow-up и нет отвеченного-непроверенного вопроса - возвращает следующий основной, LLM не вызывается")
        void returnsNextMainWhenNoLastAnsweredUnchecked() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion nextMain = unansweredMain(UUID.randomUUID(), 3);
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(nextMain));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextMain.getId(), 3, nextMain.getQuestionText(), false, null, null, null);
            when(trainingQuestionMapper.toDto(nextMain)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("В кейсе уже максимум уточнений - markFollowUpChecked, LLM не вызывается, возвращается следующий основной")
        void marksFollowUpCheckedAndReturnsNextMainWhenLimitReached() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));

            List<TrainingQuestion> maxFollowUps = IntStream.rangeClosed(1, TrainingService.MAX_FOLLOW_UPS_PER_QUESTION)
                    .mapToObj(i -> answeredFollowUp(UUID.randomUUID(), answered.getId(), i + 1))
                    .toList();
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(maxFollowUps);

            TrainingQuestion nextMain = unansweredMain(UUID.randomUUID(), 2);
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(nextMain));
            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextMain.getId(), 2, nextMain.getQuestionText(), false, null, null, null);
            when(trainingQuestionMapper.toDto(nextMain)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).markFollowUpChecked(answered.getId());
            verify(trainingWriter, never()).saveFollowUp(any(), any(), any());
            verifyNoInteractions(llmService);
        }

        @Test
        @DisplayName("LLM решил не уточнять (askFollowUp=false) - markFollowUpChecked, возвращается следующий основной")
        void marksFollowUpCheckedWhenLlmDecidesNotToAsk() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(List.of());
            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(false, null));

            TrainingQuestion nextMain = unansweredMain(UUID.randomUUID(), 2);
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(nextMain));
            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextMain.getId(), 2, nextMain.getQuestionText(), false, null, null, null);
            when(trainingQuestionMapper.toDto(nextMain)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).markFollowUpChecked(answered.getId());
            verify(trainingWriter, never()).saveFollowUp(any(), any(), any());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   "})
        @DisplayName("LLM решил уточнить, но текст вопроса null/пустой - трактуется как отказ: markFollowUpChecked, возвращается следующий основной")
        void treatsBlankOrNullQuestionAsRefusal(String question) {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(List.of());
            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(true, question));

            TrainingQuestion nextMain = unansweredMain(UUID.randomUUID(), 2);
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(nextMain));
            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextMain.getId(), 2, nextMain.getQuestionText(), false, null, null, null);
            when(trainingQuestionMapper.toDto(nextMain)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).markFollowUpChecked(answered.getId());
            verify(trainingWriter, never()).saveFollowUp(any(), any(), any());
        }

        @Test
        @DisplayName("LLM решил уточнить - сохраняет follow-up через trainingWriter, в запросе - профессия/уровень сессии, текст/ответ основного вопроса и история уточнений")
        void savesFollowUpWhenLlmDecidesToAskFollowUp() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));

            TrainingQuestion previousFollowUp = answeredFollowUp(UUID.randomUUID(), answered.getId(), 2);
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(List.of(previousFollowUp));

            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(true, "Новое уточнение"));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    UUID.randomUUID(), 3, "Новое уточнение", true, null, null, null);
            when(trainingWriter.saveFollowUp(answered.getId(), answered.getId(), "Новое уточнение"))
                    .thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingFollowUpRequest> captor = ArgumentCaptor.forClass(LlmTrainingFollowUpRequest.class);
            verify(llmService).decideTrainingFollowUp(captor.capture());
            LlmTrainingFollowUpRequest llmRequest = captor.getValue();
            assertThat(llmRequest.profession()).isEqualTo(PROFESSION);
            assertThat(llmRequest.level()).isEqualTo("Middle");
            assertThat(llmRequest.question()).isEqualTo(answered.getQuestionText());
            assertThat(llmRequest.answer()).isEqualTo(answered.getAnswerText());
            assertThat(llmRequest.previousFollowUps()).containsExactly(
                    new LlmTrainingFollowUp(previousFollowUp.getQuestionText(), previousFollowUp.getAnswerText()));

            verify(trainingWriter, never()).markFollowUpChecked(any());
        }

        @Test
        @DisplayName("Последний отвеченный вопрос сам - уточнение: caseMainId берётся из parentQuestionId, в промпт уходит текст/ответ основного вопроса")
        void usesParentMainWhenLastAnsweredIsFollowUp() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            TrainingQuestion caseMain = answeredMain(UUID.randomUUID(), 1);
            TrainingQuestion lastAnsweredFollowUp = answeredFollowUp(UUID.randomUUID(), caseMain.getId(), 2);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId))
                    .thenReturn(Optional.of(lastAnsweredFollowUp));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(caseMain.getId()))
                    .thenReturn(List.of(lastAnsweredFollowUp));
            when(trainingQuestionRepository.findById(caseMain.getId())).thenReturn(Optional.of(caseMain));

            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(true, "Ещё уточнение"));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    UUID.randomUUID(), 3, "Ещё уточнение", true, null, null, null);
            when(trainingWriter.saveFollowUp(lastAnsweredFollowUp.getId(), caseMain.getId(), "Ещё уточнение"))
                    .thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingFollowUpRequest> captor = ArgumentCaptor.forClass(LlmTrainingFollowUpRequest.class);
            verify(llmService).decideTrainingFollowUp(captor.capture());
            assertThat(captor.getValue().question()).isEqualTo(caseMain.getQuestionText());
            assertThat(captor.getValue().answer()).isEqualTo(caseMain.getAnswerText());

            verify(trainingWriter).saveFollowUp(lastAnsweredFollowUp.getId(), caseMain.getId(), "Ещё уточнение");
        }

        @Test
        @DisplayName("Основной вопрос кейса не найден по parentQuestionId - NotFoundException")
        void throwsNotFoundWhenCaseMainMissing() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            UUID missingMainId = UUID.randomUUID();
            TrainingQuestion lastAnsweredFollowUp = answeredFollowUp(UUID.randomUUID(), missingMainId, 2);
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId))
                    .thenReturn(Optional.of(lastAnsweredFollowUp));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(missingMainId))
                    .thenReturn(List.of(lastAnsweredFollowUp));
            when(trainingQuestionRepository.findById(missingMainId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("saveFollowUp бросает DataIntegrityViolationException, конкурентный follow-up уже создан - возвращает его")
        void returnsConcurrentFollowUpWhenSaveFollowUpConflicts() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            TrainingQuestion concurrentFollowUp = unansweredFollowUp(UUID.randomUUID(), answered.getId(), 2);

            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId))
                    .thenReturn(Optional.empty(), Optional.of(concurrentFollowUp));
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(List.of());
            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(true, "Уточнение"));
            when(trainingWriter.saveFollowUp(answered.getId(), answered.getId(), "Уточнение"))
                    .thenThrow(new DataIntegrityViolationException("concurrent follow-up"));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    concurrentFollowUp.getId(), 2, concurrentFollowUp.getQuestionText(), true, null, null, null);
            when(trainingQuestionMapper.toDto(concurrentFollowUp)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("saveFollowUp бросает DataIntegrityViolationException, конкурентного follow-up нет - падает дальше к следующему основному")
        void fallsBackToNextMainWhenSaveFollowUpConflictsWithoutConcurrentFollowUp() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            TrainingQuestion answered = answeredMain(UUID.randomUUID(), 1);
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId))
                    .thenReturn(Optional.empty(), Optional.empty());
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(trainingQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId()))
                    .thenReturn(List.of());
            when(llmService.decideTrainingFollowUp(any()))
                    .thenReturn(new LlmTrainingFollowUpDecision(true, "Уточнение"));
            when(trainingWriter.saveFollowUp(answered.getId(), answered.getId(), "Уточнение"))
                    .thenThrow(new DataIntegrityViolationException("concurrent follow-up"));

            TrainingQuestion nextMain = unansweredMain(UUID.randomUUID(), 2);
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(nextMain));
            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextMain.getId(), 2, nextMain.getQuestionText(), false, null, null, null);
            when(trainingQuestionMapper.toDto(nextMain)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Нет ни неотвеченного follow-up, ни основного вопроса - ConflictException")
        void throwsConflictWhenNoFollowUpAndNoMainLeft() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());
            when(trainingQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.empty());
            when(trainingQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question cap reached");
            verifyNoInteractions(llmService, trainingWriter);
        }
    }

    @Nested
    @DisplayName("SubmitAnswer")
    class SubmitAnswer {

        private TrainingQuestion aSubmittableQuestion(TrainingSession session) {
            return TrainingQuestion.builder()
                    .id(UUID.randomUUID())
                    .trainingSession(session)
                    .questionText("Вопрос")
                    .orderIndex(1)
                    .answered(false)
                    .build();
        }

        @Test
        @DisplayName("Валидный ответ на CREATED-сессию - сохраняет ответ, переводит сессию в IN_PROGRESS")
        void savesAnswerAndMovesSessionToInProgress() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, question.getId(), "Мой ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when
            trainingService.submitAnswer(request);

            // then
            assertThat(question.getAnswerText()).isEqualTo("Мой ответ");
            assertThat(question.isAnswered()).isTrue();
            assertThat(question.getAnsweredAt()).isNotNull();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Ответ на вопрос IN_PROGRESS-сессии - статус сессии не меняется")
        void keepsSessionStatusWhenAlreadyInProgress() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(SessionStatus.IN_PROGRESS);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when
            trainingService.submitAnswer(request);

            // then
            assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            UUID questionId = UUID.randomUUID();
            SubmitAnswerRequest request = new SubmitAnswerRequest(
                    UUID.randomUUID(), UUID.randomUUID(), questionId, "Ответ");
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.submitAnswer(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
        }

        @Test
        @DisplayName("Вопрос принадлежит другому пользователю - ForbiddenException")
        void throwsWhenQuestionOwnedByAnotherUser() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, ownerId, PROFESSION);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(
                    UUID.randomUUID(), sessionId, question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.submitAnswer(request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");
        }

        @Test
        @DisplayName("Вопрос принадлежит другой сессии, чем в запросе - ConflictException")
        void throwsWhenQuestionBelongsToAnotherSession() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(
                    userId, UUID.randomUUID(), question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionAlreadyCompleted() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(SessionStatus.COMPLETED);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @Test
        @DisplayName("Вопрос уже отвечен - ConflictException")
        void throwsWhenQuestionAlreadyAnswered() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aSubmittableQuestion(session);
            question.setAnswered(true);
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.submitAnswer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question already answered");
        }
    }

    @Nested
    @DisplayName("CreateReport")
    class CreateReport {

        @Test
        @DisplayName("Передаёт profession сессии как есть в LlmTrainingReportRequest")
        void passesSessionProfessionAsIsToLlmRequest() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(List.of(), "Общий фидбэк по тренировке");
            when(llmService.createTrainingReport(any())).thenReturn(llmReport);

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, PROFESSION, TOPIC, Level.MIDDLE, 4.0,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingWriter.completeReport(sessionId, llmReport)).thenReturn(expectedResponse);

            // when
            var result = trainingService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingReportRequest> captor = ArgumentCaptor.forClass(LlmTrainingReportRequest.class);
            verify(llmService).createTrainingReport(captor.capture());
            assertThat(captor.getValue().profession()).isEqualTo(PROFESSION);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(SessionStatus.COMPLETED);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Отвечено меньше минимума основных вопросов - ConflictException")
        void throwsWhenNotEnoughAnsweredQuestions() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Not enough answered questions to finish");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("completeReport бросает DataIntegrityViolationException - ConflictException")
        void throwsConflictWhenCompleteReportHitsConcurrentConflict() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = new LlmTrainingReport(List.of(), "Общий фидбэк по тренировке");
            when(llmService.createTrainingReport(any())).thenReturn(llmReport);
            when(trainingWriter.completeReport(sessionId, llmReport))
                    .thenThrow(new DataIntegrityViolationException("session already completed concurrently"));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }
    }

    @Nested
    @DisplayName("GetReport")
    class GetReport {

        @Test
        @DisplayName("Отчёт есть - маппит через trainingReportMapper с отсортированными отвеченными вопросами")
        void returnsMappedReport() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingReport report = TrainingReport.builder()
                    .id(UUID.randomUUID()).trainingSession(session).avgScore(4.0).overallFeedback("Фидбэк").build();
            session.setReport(report);
            session.setQuestions(List.of(aQuestion(2), aQuestion(1)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    report.getId(), sessionId, PROFESSION, TOPIC, Level.MIDDLE, 4.0, "Фидбэк", null, List.of());
            when(trainingReportMapper.toResponse(eq(report), eq(session), any())).thenReturn(expectedResponse);

            // when
            var result = trainingService.getReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TrainingQuestion>> captor = ArgumentCaptor.forClass(List.class);
            verify(trainingReportMapper).toResponse(eq(report), eq(session), captor.capture());
            assertThat(captor.getValue()).extracting(TrainingQuestion::getOrderIndex).containsExactly(1, 2);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.getReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException")
        void throwsWhenSessionOwnedByAnotherUser() {
            // given
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, UUID.randomUUID(), PROFESSION);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.getReport(sessionId, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
        }

        @Test
        @DisplayName("Отчёт ещё не сформирован - NotFoundException")
        void throwsWhenReportNotYetGenerated() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.getReport(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Report not found");
            verifyNoInteractions(trainingReportMapper);
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Сессия принадлежит пользователю - удаляет её")
        void deletesOwnedSession() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.existsByIdAndUserId(sessionId, userId)).thenReturn(true);

            // when
            trainingService.delete(sessionId, userId);

            // then
            verify(trainingSessionRepository).deleteById(sessionId);
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException, удаление не происходит")
        void throwsWhenSessionNotOwned() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.existsByIdAndUserId(sessionId, userId)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> trainingService.delete(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verify(trainingSessionRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("SuggestProfessions")
    class SuggestProfessions {

        @Test
        @DisplayName("Маппит найденные профессии словаря в имена с сохранением порядка")
        void mapsSuggestedProfessionsToNamesPreservingOrder() {
            // given
            ProfessionDict first = ProfessionDict.builder().id(UUID.randomUUID()).name("Java-разработчик").build();
            ProfessionDict second = ProfessionDict.builder().id(UUID.randomUUID()).name("JavaScript-разработчик").build();
            when(professionDictRepository.suggest("ja", TrainingService.SUGGEST_LIMIT)).thenReturn(List.of(first, second));

            // when
            List<String> result = trainingService.suggestProfessions("ja");

            // then
            assertThat(result).containsExactly("Java-разработчик", "JavaScript-разработчик");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"j", " j "})
        @DisplayName("Запрос короче 2 символов (null, 1 символ, 1 символ после strip) - пустой список без обращения к репозиторию")
        void returnsEmptyListForTooShortQuery(String query) {
            // when
            List<String> result = trainingService.suggestProfessions(query);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(professionDictRepository);
        }

        @Test
        @DisplayName("Экранирует спецсимволы LIKE перед обращением к репозиторию")
        void escapesLikeSpecialCharsBeforeQuerying() {
            // given
            when(professionDictRepository.suggest(anyString(), anyInt())).thenReturn(List.of());

            // when
            trainingService.suggestProfessions("100%_x\\");

            // then
            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            verify(professionDictRepository).suggest(queryCaptor.capture(), eq(TrainingService.SUGGEST_LIMIT));
            assertThat(queryCaptor.getValue()).isEqualTo("100\\%\\_x\\\\");
        }

        @Test
        @DisplayName("Обрезает пробелы по краям запроса перед обращением к репозиторию")
        void stripsQueryBeforeQuerying() {
            // given
            when(professionDictRepository.suggest("java", TrainingService.SUGGEST_LIMIT)).thenReturn(List.of());

            // when
            trainingService.suggestProfessions("  java  ");

            // then
            verify(professionDictRepository).suggest("java", TrainingService.SUGGEST_LIMIT);
        }
    }

    @Nested
    @DisplayName("SuggestTopics")
    class SuggestTopics {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"  "})
        @DisplayName("Профессия null или пробельная - пустой список без обращения к репозиторию")
        void returnsEmptyListWhenProfessionBlank(String profession) {
            // when
            List<String> result = trainingService.suggestTopics(profession, "java");

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(topicDictRepository);
        }

        @Test
        @DisplayName("Маппит найденные темы словаря в имена с сохранением порядка")
        void mapsSuggestedTopicsToNamesPreservingOrder() {
            // given
            TopicDict first = TopicDict.builder().id(UUID.randomUUID()).professionId(UUID.randomUUID())
                    .name("Spring Boot").build();
            TopicDict second = TopicDict.builder().id(UUID.randomUUID()).professionId(UUID.randomUUID())
                    .name("Spring Security").build();
            when(topicDictRepository.suggest(PROFESSION, "sp", TrainingService.SUGGEST_LIMIT))
                    .thenReturn(List.of(first, second));

            // when
            List<String> result = trainingService.suggestTopics(PROFESSION, "sp");

            // then
            assertThat(result).containsExactly("Spring Boot", "Spring Security");
        }
    }

    @Nested
    @DisplayName("NormalizeInput")
    class NormalizeInput {

        @Test
        @DisplayName("Полный ввод с профессией и темой - стрипает поля в LLM-запросе, собирает ответ из полей LLM-ответа")
        void fullInputStripsFieldsAndMapsLlmResponse() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest("  джава дев  ", " спринг ");
            LlmInputNormalization llmResponse = new LlmInputNormalization(
                    true, List.of("Java-разработчик"), true, List.of("Spring"), true);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().profession()).isEqualTo("джава дев");
            assertThat(captor.getValue().topic()).isEqualTo("спринг");

            assertThat(result.professionRecognized()).isTrue();
            assertThat(result.professionSuggestions()).containsExactly("Java-разработчик");
            assertThat(result.topicRecognized()).isTrue();
            assertThat(result.topicSuggestions()).containsExactly("Spring");
            assertThat(result.topicFitsProfession()).isTrue();
        }

        @Test
        @DisplayName("Тема null - в LLM-запрос уходит пустая строка, поля темы в ответе - null")
        void nullTopicSendsEmptyStringAndNullsTopicFields() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest(PROFESSION, null);
            LlmInputNormalization llmResponse = new LlmInputNormalization(
                    true, List.of(PROFESSION), true, List.of("Spring"), true);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().topic()).isEmpty();

            assertThat(result.topicRecognized()).isNull();
            assertThat(result.topicSuggestions()).isNull();
            assertThat(result.topicFitsProfession()).isNull();
        }

        @Test
        @DisplayName("Тема из пробелов - трактуется как отсутствующая: пустая строка в LLM-запрос, null-поля темы в ответе")
        void blankTopicSendsEmptyStringAndNullsTopicFields() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest(PROFESSION, "   ");
            LlmInputNormalization llmResponse = new LlmInputNormalization(
                    true, List.of(PROFESSION), true, List.of("Spring"), true);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().topic()).isEmpty();

            assertThat(result.topicRecognized()).isNull();
            assertThat(result.topicSuggestions()).isNull();
            assertThat(result.topicFitsProfession()).isNull();
        }

        @Test
        @DisplayName("LLM вернул null-списки подсказок - в ответе пустые списки, а не null")
        void nullSuggestionListsFromLlmBecomeEmptyLists() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest(PROFESSION, TOPIC);
            LlmInputNormalization llmResponse = new LlmInputNormalization(
                    false, null, false, null, false);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            assertThat(result.professionSuggestions()).isNotNull().isEmpty();
            assertThat(result.topicSuggestions()).isNotNull().isEmpty();
        }
    }
}
