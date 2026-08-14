package ru.workbit.training.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.content.model.BankQuestion;
import ru.workbit.content.model.DictStatus;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.model.SkillDict;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.content.repository.QuestionBankRepository;
import ru.workbit.content.repository.SkillDictRepository;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.exception.PaymentRequiredException;
import ru.workbit.exception.UnprocessableEntityException;
import ru.workbit.training.dto.CreateSessionRequest;
import ru.workbit.training.dto.FeedbackRequest;
import ru.workbit.training.dto.NormalizeInputRequest;
import ru.workbit.training.dto.NormalizeInputResponse;
import ru.workbit.training.dto.ReferenceAnswerResponse;
import ru.workbit.training.dto.SubmitAnswerRequest;
import ru.workbit.training.dto.TrainingOptionsResponse;
import ru.workbit.training.dto.TrainingQuestionResponse;
import ru.workbit.training.dto.TrainingReportResponse;
import ru.workbit.training.dto.TrainingSessionResponse;
import ru.workbit.training.dto.TrainingSkillMatch;
import ru.workbit.training.model.TrainingQuestion;
import ru.workbit.training.model.TrainingReport;
import ru.workbit.training.model.TrainingSession;
import ru.workbit.training.model.TrainingUserFeedback;
import ru.workbit.training.model.mapper.TrainingQuestionMapper;
import ru.workbit.training.model.mapper.TrainingReportMapper;
import ru.workbit.training.model.mapper.TrainingSessionMapper;
import ru.workbit.training.repository.TrainingQuestionRepository;
import ru.workbit.training.repository.TrainingSessionRepository;
import ru.workbit.training.repository.TrainingUserFeedbackRepository;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.llm.service.LlmService;
import ru.workbit.util.DictText;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrainingServiceTest")
class TrainingServiceTest {

    private static final String PROFESSION = "Java-разработчик";
    private static final String SKILL = "Spring Boot";

    @Mock
    TrainingSessionRepository trainingSessionRepository;
    @Mock
    TrainingQuestionRepository trainingQuestionRepository;
    @Mock
    TrainingUserFeedbackRepository trainingUserFeedbackRepository;
    @Mock
    ProfessionDictRepository professionDictRepository;
    @Mock
    SkillDictRepository skillDictRepository;
    @Mock
    QuestionBankRepository questionBankRepository;
    @Mock
    TrainingWriter trainingWriter;
    @Mock
    LlmService llmService;
    @Mock
    QuotaService quotaService;
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
                .skill(SKILL)
                .profession(profession)
                .level(TrainingSession.Level.MIDDLE)
                .status(TrainingSession.Status.CREATED)
                .build();
    }

    private TrainingQuestion aQuestion(int orderIndex) {
        return TrainingQuestion.builder()
                .id(UUID.randomUUID())
                .text("Вопрос " + orderIndex)
                .answerText("Ответ " + orderIndex)
                .orderIndex(orderIndex)
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
        private final UUID skillId = UUID.randomUUID();

        private TrainingSession mappedEntity(String skill, String profession) {
            return TrainingSession.builder()
                    .skill(skill)
                    .profession(profession)
                    .level(TrainingSession.Level.MIDDLE)
                    .build();
        }

        private void stubProfessionApproved() {
            ProfessionDict professionDict = ProfessionDict.builder()
                    .id(professionId)
                    .name(PROFESSION)
                    .status(DictStatus.APPROVED)
                    .build();
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.of(professionDict));
        }

        private void stubProfessionAndSkillApproved() {
            stubProfessionApproved();
            SkillDict skillDict = SkillDict.builder()
                    .id(skillId)
                    .name(SKILL)
                    .status(DictStatus.APPROVED)
                    .build();
            when(skillDictRepository.findByProfessionIdAndMatchKey(professionId, DictText.matchKey(SKILL)))
                    .thenReturn(Optional.of(skillDict));
        }

        @Test
        @DisplayName("Банк выдал полные QUESTION_CAP вопросов - LLM не вызывается, в writer уходят банковские и пустой список сгенерированных")
        void fullBankSkipsLlmGeneration() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
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
        @DisplayName("Банк выдал часть вопросов (7 из 10) - LLM вызывается с count=3, текстами банковских вопросов и грейдом сессии")
        void partialBankRequestsMissingFromLlm() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(7);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Сгенерированный 1", "Сгенерированный 2", "Сгенерированный 3");
            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            verify(questionBankRepository).sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(eq("middle"), captor.capture());
            LlmTrainingQuestionsRequest llmRequest = captor.getValue();
            assertThat(llmRequest.skill()).isEqualTo(SKILL);
            assertThat(llmRequest.profession()).isEqualTo(PROFESSION);
            assertThat(llmRequest.count()).isEqualTo(3);
            assertThat(llmRequest.existingQuestions())
                    .containsExactlyElementsOf(bank.stream().map(BankQuestion::getText).toList());

            verify(trainingWriter).createSession(mappedEntity, bank, generated);
        }

        @ParameterizedTest
        @EnumSource(TrainingSession.Level.class)
        @DisplayName("Грейд, передаваемый в generateTrainingQuestions, - это getGrade() уровня сессии")
        void passesSessionLevelGradeToLlm(TrainingSession.Level level) {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, level);
            TrainingSession mappedEntity = TrainingSession.builder().skill(SKILL).profession(PROFESSION).level(level).build();
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, level.name(), userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(anyString(), any()))
                    .thenReturn(new LlmTrainingQuestions(List.of("Q1", "Q2", "Q3")));

            // when
            trainingService.create(request, userId);

            // then
            verify(llmService).generateTrainingQuestions(eq(level.getGrade()), any());
        }

        @Test
        @DisplayName("Банк пуст - LLM запрашивается на полный батч из QUESTION_CAP вопросов")
        void emptyBankRequestsFullBatch() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());

            List<String> generated = IntStream.rangeClosed(1, TrainingService.QUESTION_CAP)
                    .mapToObj(i -> "Сгенерированный " + i)
                    .toList();
            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, List.of(), generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(eq("middle"), captor.capture());
            assertThat(captor.getValue().count()).isEqualTo(TrainingService.QUESTION_CAP);
            assertThat(captor.getValue().existingQuestions()).isEmpty();

            verify(trainingWriter).createSession(mappedEntity, List.of(), generated);
        }

        @Test
        @DisplayName("Ответ LLM с null/blank и лишними строками - фильтруется и обрезается до missing")
        void filtersAndTrimsLlmResponse() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(8);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(
                    Arrays.asList(null, "   ", "Годный вопрос 1", "Годный вопрос 2", "Лишний вопрос 3")));

            List<String> expectedGenerated = List.of("Годный вопрос 1", "Годный вопрос 2");
            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(8);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Единственный сгенерированный");
            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 9, null, null);
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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(null));

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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(anyString(), any()))
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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(2);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(List.of()));

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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(1);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(anyString(), any()))
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
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(3);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);
            when(llmService.generateTrainingQuestions(anyString(), any())).thenReturn(new LlmTrainingQuestions(List.of()));

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 3, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).createSession(mappedEntity, bank, List.of());
        }

        @Test
        @DisplayName("Словарь подтвердил и навык, и профессию - LLM normalizeInput не вызывается")
        void dictionaryFastPathSkipsLlmNormalization() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(llmService, never()).normalizeInput(any());
        }

        @Test
        @DisplayName("Ввод отличается от словарного по формулировке, но совпадает по matchKey - подменяется словарным названием, LLM не вызывается")
        void replacesInputWithDictionaryNameWhenMatchKeyMatches() {
            // given
            String skillInput = "Spring Boot Jpa";
            String professionInput = "Разработчик на Java";
            CreateSessionRequest request = new CreateSessionRequest(skillInput, professionInput, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(skillInput, professionInput);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);

            ProfessionDict professionDict = ProfessionDict.builder()
                    .id(professionId).name(PROFESSION).status(DictStatus.APPROVED).build();
            when(professionDictRepository.findByMatchKey(DictText.matchKey(professionInput)))
                    .thenReturn(Optional.of(professionDict));
            SkillDict skillDict = SkillDict.builder()
                    .id(skillId).name("Spring JPA").status(DictStatus.APPROVED).build();
            when(skillDictRepository.findByProfessionIdAndMatchKey(professionId, DictText.matchKey(skillInput)))
                    .thenReturn(Optional.of(skillDict));

            when(trainingWriter.upsertDictionaries("Spring JPA", PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, "Spring JPA", PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getSkill()).isEqualTo("Spring JPA");
            assertThat(mappedEntity.getProfession()).isEqualTo(PROFESSION);
            verifyNoInteractions(llmService);
            verify(trainingWriter).upsertDictionaries("Spring JPA", PROFESSION);
        }

        @Test
        @DisplayName("Профессия найдена в словаре по ключу, навык не найден в её скоупе, LLM отверг профессию но признал навык - вердикт LLM по профессии игнорируется, LLM normalizeInput вызывается")
        void professionKnownSkillUnknownTriggersLlmNormalizationForSkillOnly() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);

            stubProfessionApproved();
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(true, List.of(), false, List.of(), true));

            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(PROFESSION);
            verify(llmService).normalizeInput(any());
            verify(skillDictRepository).findByProfessionIdAndMatchKey(professionId, DictText.matchKey(SKILL));
        }

        @Test
        @DisplayName("Профессия и навык не в словаре - в запрос нормализатора уходят кандидаты, найденные по значащим словам обоих словарей")
        void sendsCandidatesFromBothDictionariesToNormalizer() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            List<String> skillCandidates = List.of("Spring Framework");
            List<String> professionCandidates = List.of("Java Developer");
            when(skillDictRepository.findCandidateNames(DictText.matchTokens(SKILL), TrainingService.CANDIDATE_LIMIT))
                    .thenReturn(skillCandidates);
            when(professionDictRepository.findCandidateNames(DictText.matchTokens(PROFESSION), TrainingService.CANDIDATE_LIMIT))
                    .thenReturn(professionCandidates);
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(true, List.of(), true, List.of(), true));

            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);
            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            trainingService.create(request, userId);

            // then
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().skill()).isEqualTo(SKILL);
            assertThat(captor.getValue().profession()).isEqualTo(PROFESSION);
            assertThat(captor.getValue().knownSkills()).isEqualTo(skillCandidates);
            assertThat(captor.getValue().knownProfessions()).isEqualTo(professionCandidates);
        }

        @Test
        @DisplayName("Навык без значащих токенов (только пунктуация) - кандидаты по навыку не запрашиваются, в запрос уходит пустой список")
        void doesNotQueryCandidatesWhenSkillHasNoSignificantTokens() {
            // given
            String punctuationSkill = "!!!";
            CreateSessionRequest request = new CreateSessionRequest(punctuationSkill, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(punctuationSkill, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(false, List.of(), true, List.of(), true));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Skill not recognized");

            verify(skillDictRepository, never()).findCandidateNames(any(), anyInt());
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().knownSkills()).isEmpty();
        }

        @Test
        @DisplayName("Профессия без значащих токенов (только пунктуация) - кандидаты по профессии не запрашиваются, в запрос уходит пустой список")
        void doesNotQueryCandidatesWhenProfessionHasNoSignificantTokens() {
            // given
            String punctuationProfession = "!!!";
            CreateSessionRequest request = new CreateSessionRequest(SKILL, punctuationProfession, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, punctuationProfession);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(punctuationProfession))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(true, List.of(), false, List.of(), true));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Profession not recognized");

            verify(professionDictRepository, never()).findCandidateNames(any(), anyInt());
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().knownProfessions()).isEmpty();
        }

        @Test
        @DisplayName("Профессия не в словаре, среди подсказок LLM известна не первая, а вторая - выбирается она, в сессию идёт словарное название, а не текст подсказки")
        void picksKnownSuggestionEvenWhenNotFirstAndUsesDictionaryName() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            String firstSuggestion = "Джавист";
            String secondSuggestion = "Java Developer";
            String dictionaryName = "Java-инженер";
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, List.of(firstSuggestion, secondSuggestion), true));

            when(professionDictRepository.findByMatchKey(DictText.matchKey(firstSuggestion))).thenReturn(Optional.empty());
            ProfessionDict known = ProfessionDict.builder()
                    .id(professionId).name(dictionaryName).status(DictStatus.APPROVED).build();
            when(professionDictRepository.findByMatchKey(DictText.matchKey(secondSuggestion)))
                    .thenReturn(Optional.of(known));

            when(trainingWriter.upsertDictionaries(SKILL, dictionaryName))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, dictionaryName, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(dictionaryName);
            verify(trainingWriter).upsertDictionaries(SKILL, dictionaryName);
        }

        @Test
        @DisplayName("Профессия не в словаре, LLM распознал навык, но не распознал профессию - UnprocessableEntityException, сессия не создаётся")
        void throwsWhenProfessionNotRecognizedByLlm() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(true, List.of(), false, List.of("Java Developer"), true));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Profession not recognized");
            verify(llmService, never()).generateTrainingQuestions(any(), any());
            verify(skillDictRepository, never()).findByProfessionIdAndMatchKey(any(), any());
            verifyNoInteractions(trainingWriter, questionBankRepository);
        }

        @Test
        @DisplayName("Профессия не в словаре, LLM не распознал навык - UnprocessableEntityException (проверка навыка идёт первой), сессия не создаётся")
        void throwsWhenSkillNotRecognizedByLlm() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any()))
                    .thenReturn(new LlmInputNormalization(false, List.of("Spring Framework"), true, List.of(), false));

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(UnprocessableEntityException.class)
                    .hasMessage("Skill not recognized");
            verify(llmService, never()).generateTrainingQuestions(any(), any());
            verifyNoInteractions(trainingWriter, questionBankRepository);
        }

        @Test
        @DisplayName("Профессия мимо словаря, LLM распознал и вернул подсказки - сессия создаётся с канонической профессией из первой подсказки, а не с вводом пользователя")
        void canonicalizesProfessionFromFirstSuggestionWhenNotInDictionary() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, List.of("Java-инженер", "Java Developer"), true));

            when(trainingWriter.upsertDictionaries(SKILL, "Java-инженер"))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, "Java-инженер", TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo("Java-инженер");
            assertThat(mappedEntity.getSkill()).isEqualTo(SKILL);
            verify(trainingWriter).upsertDictionaries(SKILL, "Java-инженер");
        }

        @Test
        @DisplayName("Профессия APPROVED в словаре, навык мимо словаря, LLM распознал навык и вернул подсказки - канонизируется только навык, профессия остаётся как есть")
        void canonicalizesSkillFromFirstSuggestionWhenNotApprovedKeepingProfessionUnchanged() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionApproved();
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of("Spring Framework", "Spring MVC"), true, List.of(), true));

            when(trainingWriter.upsertDictionaries("Spring Framework", PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, "Spring Framework", PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(PROFESSION);
            assertThat(mappedEntity.getSkill()).isEqualTo("Spring Framework");
            verify(trainingWriter).upsertDictionaries("Spring Framework", PROFESSION);
        }

        @ParameterizedTest
        @MethodSource("unusableSuggestionSources")
        @DisplayName("LLM признал профессию распознанной, но подсказки не пригодны (null/пусто/только blank) - ввод пользователя сохраняется как есть, исключения нет")
        void keepsUserInputWhenSuggestionsUnusable(List<String> professionSuggestions) {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, professionSuggestions, true));

            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(PROFESSION);
            verify(trainingWriter).upsertDictionaries(SKILL, PROFESSION);
        }

        private static Stream<Arguments> unusableSuggestionSources() {
            return Stream.of(
                    Arguments.of((Object) null),
                    Arguments.of(List.of()),
                    Arguments.of(Arrays.asList(" ", "", null)));
        }

        @Test
        @DisplayName("Первая подсказка длиннее MAX_INPUT_LENGTH - пропускается, берётся следующая пригодная (ровно MAX_INPUT_LENGTH символов - валидна)")
        void skipsSuggestionLongerThanMaxLength() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            String tooLong = "A".repeat(TrainingService.MAX_INPUT_LENGTH + 1);
            String atLimit = "B".repeat(TrainingService.MAX_INPUT_LENGTH);
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, List.of(tooLong, atLimit), true));

            when(trainingWriter.upsertDictionaries(SKILL, atLimit))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, atLimit, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(atLimit);
            verify(trainingWriter).upsertDictionaries(SKILL, atLimit);
        }

        @Test
        @DisplayName("Все подсказки длиннее MAX_INPUT_LENGTH - пригодных нет, остаётся ввод пользователя")
        void keepsUserInputWhenAllSuggestionsExceedMaxLength() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            String tooLong1 = "A".repeat(TrainingService.MAX_INPUT_LENGTH + 1);
            String tooLong2 = "B".repeat(TrainingService.MAX_INPUT_LENGTH + 5);
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, List.of(tooLong1, tooLong2), true));

            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(PROFESSION);
            verify(trainingWriter).upsertDictionaries(SKILL, PROFESSION);
        }

        @Test
        @DisplayName("Подсказка со слешем и пробелами по краям - применяется strip()")
        void stripsSuggestionWithSlashAndSurroundingWhitespace() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            String rawSuggestion = "  Java / Kotlin разработчик  ";
            String strippedSuggestion = "Java / Kotlin разработчик";
            when(llmService.normalizeInput(any())).thenReturn(new LlmInputNormalization(
                    true, List.of(), true, List.of(rawSuggestion), true));

            when(trainingWriter.upsertDictionaries(SKILL, strippedSuggestion))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));
            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, SKILL, strippedSuggestion, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 10, null, null);
            when(trainingWriter.createSession(mappedEntity, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(mappedEntity.getProfession()).isEqualTo(strippedSuggestion);
            verify(trainingWriter).upsertDictionaries(SKILL, strippedSuggestion);
        }

        @Test
        @DisplayName("Проверка квоты тренировок выполняется до генерации вопросов LLM")
        void checksQuotaBeforeGeneratingQuestions() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            TrainingSession mappedEntity = mappedEntity(SKILL, PROFESSION);
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);
            stubProfessionAndSkillApproved();
            when(trainingWriter.upsertDictionaries(SKILL, PROFESSION))
                    .thenReturn(new TrainingWriter.DictionaryRefs(professionId, skillId));

            List<BankQuestion> bank = bankQuestions(7);
            when(questionBankRepository.sampleUnseen(
                    professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);
            List<String> generated = List.of("Сгенерированный 1", "Сгенерированный 2", "Сгенерированный 3");
            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(generated));
            when(trainingWriter.createSession(mappedEntity, bank, generated))
                    .thenReturn(mock(TrainingSessionResponse.class));

            // when
            trainingService.create(request, userId);

            // then
            InOrder order = inOrder(quotaService, llmService);
            order.verify(quotaService).checkTrainingAvailable(userId);
            order.verify(llmService).generateTrainingQuestions(eq("middle"), any());
        }

        @Test
        @DisplayName("Квота тренировок исчерпана - PaymentRequiredException пробрасывается, LLM и словари не вызываются, сессия не создаётся")
        void throwsWhenTrainingQuotaExhausted() {
            // given
            CreateSessionRequest request = new CreateSessionRequest(SKILL, PROFESSION, TrainingSession.Level.MIDDLE);
            doThrow(new PaymentRequiredException("Training quota exhausted"))
                    .when(quotaService).checkTrainingAvailable(userId);

            // when / then
            assertThatThrownBy(() -> trainingService.create(request, userId))
                    .isInstanceOf(PaymentRequiredException.class)
                    .hasMessage("Training quota exhausted");
            verifyNoInteractions(trainingSessionMapper, llmService, trainingWriter, questionBankRepository,
                    professionDictRepository, skillDictRepository);
        }
    }

    @Nested
    @DisplayName("Get")
    class Get {

        @Test
        @DisplayName("Сессия найдена - возвращает ответ с числом отвеченных и общим числом вопросов")
        void returnsResponseWithAnsweredAndTotalCounts() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.countByTrainingSessionIdAndAnsweredTrue(sessionId)).thenReturn(3L);
            when(trainingQuestionRepository.countByTrainingSessionId(sessionId)).thenReturn(10L);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 3, 10, null, null);
            when(trainingSessionMapper.toResponse(session, 3, 10)).thenReturn(expectedResponse);

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
        @DisplayName("Мапит счётчики отвеченных/всего вопросов по каждой сессии, для сессий без записи в счётчике - 0/0")
        void mapsQuestionCountsPerSessionDefaultingToZero() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionWithCounts = UUID.randomUUID();
            UUID sessionWithoutCounts = UUID.randomUUID();
            TrainingSession first = aSession(sessionWithCounts, userId, PROFESSION);
            TrainingSession second = aSession(sessionWithoutCounts, userId, PROFESSION);
            Pageable pageable = Pageable.ofSize(10);
            Page<TrainingSession> page = new PageImpl<>(List.of(first, second));
            when(trainingSessionRepository.findAllByUserId(userId, pageable)).thenReturn(page);

            TrainingQuestionRepository.QuestionCounts counts = mock(TrainingQuestionRepository.QuestionCounts.class);
            when(counts.getSessionId()).thenReturn(sessionWithCounts);
            when(counts.getAnswered()).thenReturn(5L);
            when(counts.getTotal()).thenReturn(10L);
            when(trainingQuestionRepository.countBySessionIds(List.of(sessionWithCounts, sessionWithoutCounts)))
                    .thenReturn(List.of(counts));

            TrainingSessionResponse firstResponse = new TrainingSessionResponse(
                    sessionWithCounts, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 5, 10, null, null);
            TrainingSessionResponse secondResponse = new TrainingSessionResponse(
                    sessionWithoutCounts, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, TrainingSession.Status.CREATED, 0, 0, null, null);
            when(trainingSessionMapper.toResponse(first, 5, 10)).thenReturn(firstResponse);
            when(trainingSessionMapper.toResponse(second, 0, 0)).thenReturn(secondResponse);

            // when
            Page<TrainingSessionResponse> result = trainingService.getAll(userId, pageable);

            // then
            assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        }
    }

    @Nested
    @DisplayName("FindLatestBySkills")
    class FindLatestBySkills {

        private final UUID userId = UUID.randomUUID();

        private TrainingSession sessionWithSkill(UUID id, String skill, TrainingReport report) {
            return TrainingSession.builder()
                    .id(id)
                    .userId(userId)
                    .skill(skill)
                    .profession(PROFESSION)
                    .level(TrainingSession.Level.MIDDLE)
                    .status(TrainingSession.Status.COMPLETED)
                    .report(report)
                    .build();
        }

        @Test
        @DisplayName("По каждому навыку возвращает метрики последней сессии")
        void returnsMatchForEachSkillWithCounts() {
            // given
            UUID springSessionId = UUID.randomUUID();
            UUID javaSessionId = UUID.randomUUID();
            TrainingReport report = TrainingReport.builder().avgScore(4.5).build();
            TrainingSession springSession = sessionWithSkill(springSessionId, "Spring Boot", report);
            TrainingSession javaSession = sessionWithSkill(javaSessionId, "Java Core", null);
            when(trainingSessionRepository.findAllByUserIdAndLoweredSkillIn(userId, List.of("spring boot", "java core")))
                    .thenReturn(List.of(springSession, javaSession));

            when(trainingQuestionRepository.countByTrainingSessionIdAndAnsweredTrue(springSessionId)).thenReturn(7L);
            when(trainingQuestionRepository.countByTrainingSessionId(springSessionId)).thenReturn(10L);
            when(trainingQuestionRepository.countByTrainingSessionIdAndAnsweredTrue(javaSessionId)).thenReturn(0L);
            when(trainingQuestionRepository.countByTrainingSessionId(javaSessionId)).thenReturn(10L);

            // when
            List<TrainingSkillMatch> result = trainingService.findLatestBySkills(userId, List.of("spring boot", "java core"));

            // then
            assertThat(result).containsExactlyInAnyOrder(
                    new TrainingSkillMatch(springSessionId, "Spring Boot", TrainingSession.Status.COMPLETED, 4.5, 7, 10),
                    new TrainingSkillMatch(javaSessionId, "Java Core", TrainingSession.Status.COMPLETED, null, 0, 10));
        }

        @Test
        @DisplayName("Несколько сессий с одинаковым навыком в разном регистре - берётся первая (последняя по времени), для второй счётчики не считаются")
        void deduplicatesByLoweredSkillKeepingFirstOccurrence() {
            // given
            UUID latestSessionId = UUID.randomUUID();
            UUID olderSessionId = UUID.randomUUID();
            TrainingReport latestReport = TrainingReport.builder().avgScore(5.0).build();
            TrainingSession latestSession = sessionWithSkill(latestSessionId, "SPRING BOOT", latestReport);
            TrainingSession olderSession = sessionWithSkill(olderSessionId, "spring boot", null);
            when(trainingSessionRepository.findAllByUserIdAndLoweredSkillIn(userId, List.of("spring boot")))
                    .thenReturn(List.of(latestSession, olderSession));

            when(trainingQuestionRepository.countByTrainingSessionIdAndAnsweredTrue(latestSessionId)).thenReturn(4L);
            when(trainingQuestionRepository.countByTrainingSessionId(latestSessionId)).thenReturn(10L);

            // when
            List<TrainingSkillMatch> result = trainingService.findLatestBySkills(userId, List.of("spring boot"));

            // then
            assertThat(result).containsExactly(
                    new TrainingSkillMatch(latestSessionId, "SPRING BOOT", TrainingSession.Status.COMPLETED, 5.0, 4, 10));
            verify(trainingQuestionRepository, never()).countByTrainingSessionIdAndAnsweredTrue(olderSessionId);
            verify(trainingQuestionRepository, never()).countByTrainingSessionId(olderSessionId);
        }

        @Test
        @DisplayName("Нет сессий по навыкам - пустой список, счётчики не запрашиваются")
        void returnsEmptyListWhenNoSessionsMatch() {
            // given
            when(trainingSessionRepository.findAllByUserIdAndLoweredSkillIn(userId, List.of("несуществующий навык")))
                    .thenReturn(List.of());

            // when
            List<TrainingSkillMatch> result = trainingService.findLatestBySkills(userId, List.of("несуществующий навык"));

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(trainingQuestionRepository);
        }
    }

    @Nested
    @DisplayName("NextQuestion")
    class NextQuestion {

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
            verifyNoInteractions(trainingQuestionRepository);
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionCompleted() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(TrainingSession.Status.COMPLETED);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(trainingQuestionRepository);
        }

        @Test
        @DisplayName("Есть неотвеченный вопрос - маппит и возвращает его")
        void returnsNextUnansweredQuestion() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            TrainingQuestion nextQuestion = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Вопрос 2").orderIndex(2).answered(false).build();
            when(trainingQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.of(nextQuestion));

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    nextQuestion.getId(), 2, nextQuestion.getText(), null, null, null);
            when(trainingQuestionMapper.toDto(nextQuestion)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
        }

        @Test
        @DisplayName("Неотвеченных вопросов нет - ConflictException")
        void throwsConflictWhenNoQuestionsLeft() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question cap reached");
        }
    }

    @Nested
    @DisplayName("AddQuestions")
    class AddQuestions {

        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final UUID professionId = UUID.randomUUID();
        private final UUID skillId = UUID.randomUUID();

        private TrainingSession sessionWithQuestions(List<TrainingQuestion> questions) {
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(questions);
            return session;
        }

        private void stubProfessionAndSkillFound() {
            ProfessionDict professionDict = ProfessionDict.builder().id(professionId).name(PROFESSION).build();
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.of(professionDict));
            SkillDict skillDict = SkillDict.builder().id(skillId).name(SKILL).build();
            when(skillDictRepository.findByProfessionIdAndMatchKey(professionId, DictText.matchKey(SKILL)))
                    .thenReturn(Optional.of(skillDict));
        }

        @Test
        @DisplayName("Банк выдал ровно missing вопросов - LLM не вызывается вовсе")
        void addsQuestionsEntirelyFromBankWithoutCallingLlm() {
            // given
            List<TrainingQuestion> existing = IntStream.rangeClosed(1, 5).mapToObj(i -> aQuestion(i)).toList();
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();

            List<BankQuestion> bank = bankQuestions(TrainingService.QUESTION_CAP);
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.appendQuestions(sessionId, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.addQuestions(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verifyNoInteractions(llmService);
            verify(trainingWriter).appendQuestions(sessionId, bank, List.of());
        }

        @Test
        @DisplayName("Банк выдал часть вопросов (6 из 10 missing) - LLM добирает остаток, existingQuestions - все заданные плюс только что отобранные банковские")
        void addsQuestionsPartiallyFromBankAndLlm() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2), aQuestion(3), aQuestion(4), aQuestion(5));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();

            List<BankQuestion> bank = bankQuestions(6);
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            List<String> generated = List.of("Новый 1", "Новый 2", "Новый 3", "Новый 4");
            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.appendQuestions(sessionId, bank, generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.addQuestions(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingQuestionsRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingQuestionsRequest.class);
            verify(llmService).generateTrainingQuestions(eq("middle"), captor.capture());
            LlmTrainingQuestionsRequest llmRequest = captor.getValue();
            assertThat(llmRequest.skill()).isEqualTo(SKILL);
            assertThat(llmRequest.profession()).isEqualTo(PROFESSION);
            assertThat(llmRequest.count()).isEqualTo(4);
            assertThat(llmRequest.existingQuestions()).containsExactlyElementsOf(
                    Stream.concat(existing.stream().map(TrainingQuestion::getText), bank.stream().map(BankQuestion::getText))
                            .toList());

            verify(trainingWriter).appendQuestions(sessionId, bank, generated);
        }

        @Test
        @DisplayName("Профессии нет в словаре - банк не опрашивается вовсе, вопросы даёт только LLM")
        void addsQuestionsOnlyFromLlmWhenProfessionNotInDictionary() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2), aQuestion(3));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            when(professionDictRepository.findByMatchKey(DictText.matchKey(PROFESSION))).thenReturn(Optional.empty());

            List<String> generated = IntStream.rangeClosed(1, TrainingService.QUESTION_CAP)
                    .mapToObj(i -> "Сгенерированный " + i)
                    .toList();
            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(generated));

            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.appendQuestions(sessionId, List.of(), generated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.addQuestions(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verifyNoInteractions(questionBankRepository);
            verify(skillDictRepository, never()).findByProfessionIdAndMatchKey(any(), any());
            verify(trainingWriter).appendQuestions(sessionId, List.of(), generated);
        }

        @Test
        @DisplayName("У потолка MAX_QUESTIONS остаётся укороченный запрос (45 вопросов в сессии - просят 5, а не QUESTION_CAP)")
        void shortensLastBatchNearQuestionCapLimit() {
            // given
            List<TrainingQuestion> existing = IntStream.rangeClosed(1, 45).mapToObj(i -> aQuestion(i)).toList();
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();

            List<BankQuestion> bank = bankQuestions(5);
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, 5)).thenReturn(bank);

            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.appendQuestions(sessionId, bank, List.of())).thenReturn(expectedResponse);

            // when
            var result = trainingService.addQuestions(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(questionBankRepository).sampleUnseen(professionId, skillId, "MIDDLE", userId, 5);
            verifyNoInteractions(llmService);
        }

        @Test
        @DisplayName("LLM вернул больше вопросов, чем не хватает до missing - обрезается по limit")
        void trimsExtraLlmQuestionsToMissingLimit() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();

            List<BankQuestion> bank = bankQuestions(8);
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(bank);

            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(
                    List.of("Годный 1", "Годный 2", "Лишний 3")));

            List<String> expectedGenerated = List.of("Годный 1", "Годный 2");
            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.appendQuestions(sessionId, bank, expectedGenerated)).thenReturn(expectedResponse);

            // when
            var result = trainingService.addQuestions(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).appendQuestions(sessionId, bank, expectedGenerated);
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException, словари/банк/LLM/writer не трогаются")
        void throwsWhenSessionNotFound() {
            // given
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }

        @Test
        @DisplayName("Сессия принадлежит другому пользователю - NotFoundException")
        void throwsWhenSessionOwnedByAnotherUser() {
            // given
            TrainingSession session = aSession(sessionId, UUID.randomUUID(), PROFESSION);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }

        @Test
        @DisplayName("Сессия уже завершена - ConflictException, словари/банк/LLM/writer не трогаются")
        void throwsWhenSessionCompleted() {
            // given
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(TrainingSession.Status.COMPLETED);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }

        @Test
        @DisplayName("Вопросов уже MAX_QUESTIONS - ConflictException, словари/банк/LLM/writer не трогаются")
        void throwsWhenQuestionLimitReached() {
            // given
            List<TrainingQuestion> existing = IntStream.rangeClosed(1, TrainingService.MAX_QUESTIONS)
                    .mapToObj(i -> aQuestion(i))
                    .toList();
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Question limit reached");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }

        @Test
        @DisplayName("Остался неотвеченный вопрос - ConflictException, словари/банк/LLM/writer не трогаются")
        void throwsWhenUnansweredQuestionsLeft() {
            // given
            TrainingQuestion unanswered = TrainingQuestion.builder()
                    .id(UUID.randomUUID()).text("Вопрос").orderIndex(1).answered(false).build();
            TrainingSession session = sessionWithQuestions(List.of(unanswered, aQuestion(2)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Unanswered questions left");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }

        @Test
        @DisplayName("Банк пуст и LLM вернул null-список - ConflictException (нечего добавить), writer не вызывается")
        void throwsConflictWhenBankEmptyAndLlmReturnsNullList() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2), aQuestion(3));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(eq("middle"), any())).thenReturn(new LlmTrainingQuestions(null));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No new questions available");
            verifyNoInteractions(trainingWriter);
        }

        @Test
        @DisplayName("Банк пуст и LLM вернул только blank-строки - ConflictException (нечего добавить)")
        void throwsConflictWhenBankEmptyAndLlmReturnsOnlyBlankStrings() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2), aQuestion(3));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            stubProfessionAndSkillFound();
            when(questionBankRepository.sampleUnseen(professionId, skillId, "MIDDLE", userId, TrainingService.QUESTION_CAP))
                    .thenReturn(List.of());
            when(llmService.generateTrainingQuestions(eq("middle"), any()))
                    .thenReturn(new LlmTrainingQuestions(Arrays.asList("", "   ", null)));

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No new questions available");
            verifyNoInteractions(trainingWriter);
        }

        @Test
        @DisplayName("Тариф FREE (checkPaidPlan бросает ForbiddenException) - пробрасывается, LLM и writer не вызываются")
        void throwsWhenPaidPlanRequired() {
            // given
            List<TrainingQuestion> existing = List.of(aQuestion(1), aQuestion(2), aQuestion(3));
            TrainingSession session = sessionWithQuestions(existing);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));
            doThrow(new ForbiddenException("Paid plan required")).when(quotaService).checkPaidPlan(userId);

            // when / then
            assertThatThrownBy(() -> trainingService.addQuestions(sessionId, userId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Paid plan required");
            verifyNoInteractions(llmService, trainingWriter, questionBankRepository, professionDictRepository, skillDictRepository);
        }
    }

    @Nested
    @DisplayName("SubmitAnswer")
    class SubmitAnswer {

        private TrainingQuestion aSubmittableQuestion(TrainingSession session) {
            return TrainingQuestion.builder()
                    .id(UUID.randomUUID())
                    .trainingSession(session)
                    .text("Вопрос")
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
            assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("Ответ на вопрос IN_PROGRESS-сессии - статус сессии не меняется")
        void keepsSessionStatusWhenAlreadyInProgress() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setStatus(TrainingSession.Status.IN_PROGRESS);
            TrainingQuestion question = aSubmittableQuestion(session);
            SubmitAnswerRequest request = new SubmitAnswerRequest(userId, sessionId, question.getId(), "Ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when
            trainingService.submitAnswer(request);

            // then
            assertThat(session.getStatus()).isEqualTo(TrainingSession.Status.IN_PROGRESS);
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
            session.setStatus(TrainingSession.Status.COMPLETED);
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
    @DisplayName("SubmitQuestionFeedback")
    class SubmitQuestionFeedback {

        private final UUID userId = UUID.randomUUID();
        private final UUID sessionId = UUID.randomUUID();
        private final UUID questionId = UUID.randomUUID();

        private TrainingQuestion questionInSession(UUID ownerId) {
            TrainingSession session = aSession(sessionId, ownerId, PROFESSION);
            return TrainingQuestion.builder()
                    .id(questionId).trainingSession(session).text("Вопрос").orderIndex(1).build();
        }

        @Test
        @DisplayName("Валидный запрос - сохраняет фидбэк с sessionId/questionId и полями из запроса")
        void savesFeedbackWithRequestFields() {
            // given
            TrainingQuestion question = questionInSession(userId);
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(
                    TrainingUserFeedback.Vote.DOWN, List.of("Оценка занижена"), "Комментарий");

            // when
            trainingService.submitQuestionFeedback(sessionId, questionId, userId, request);

            // then
            ArgumentCaptor<TrainingUserFeedback> captor = ArgumentCaptor.forClass(TrainingUserFeedback.class);
            verify(trainingUserFeedbackRepository).save(captor.capture());
            TrainingUserFeedback saved = captor.getValue();
            assertThat(saved.getSessionId()).isEqualTo(sessionId);
            assertThat(saved.getQuestionId()).isEqualTo(questionId);
            assertThat(saved.getVote()).isEqualTo(TrainingUserFeedback.Vote.DOWN);
            assertThat(saved.getReasons()).containsExactly("Оценка занижена");
            assertThat(saved.getComment()).isEqualTo("Комментарий");
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> trainingService.submitQuestionFeedback(sessionId, questionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verifyNoInteractions(trainingUserFeedbackRepository);
        }

        @Test
        @DisplayName("Вопрос принадлежит другому пользователю - ForbiddenException")
        void throwsWhenQuestionOwnedByAnotherUser() {
            // given
            TrainingQuestion question = questionInSession(UUID.randomUUID());
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> trainingService.submitQuestionFeedback(sessionId, questionId, userId, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");
            verifyNoInteractions(trainingUserFeedbackRepository);
        }

        @Test
        @DisplayName("Вопрос принадлежит другой сессии, чем в запросе - ConflictException")
        void throwsWhenQuestionBelongsToAnotherSession() {
            // given
            TrainingQuestion question = questionInSession(userId);
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.of(question));
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> trainingService.submitQuestionFeedback(
                    UUID.randomUUID(), questionId, userId, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");
            verifyNoInteractions(trainingUserFeedbackRepository);
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
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setReport(TrainingReport.builder().avgScore(4.0).overallFeedback("Фидбэк").build());
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), "Отлично");

            // when
            trainingService.submitReportFeedback(sessionId, userId, request);

            // then
            ArgumentCaptor<TrainingUserFeedback> captor = ArgumentCaptor.forClass(TrainingUserFeedback.class);
            verify(trainingUserFeedbackRepository).save(captor.capture());
            TrainingUserFeedback saved = captor.getValue();
            assertThat(saved.getSessionId()).isEqualTo(sessionId);
            assertThat(saved.getQuestionId()).isNull();
            assertThat(saved.getVote()).isEqualTo(TrainingUserFeedback.Vote.UP);
            assertThat(saved.getComment()).isEqualTo("Отлично");
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException")
        void throwsWhenSessionNotFound() {
            // given
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> trainingService.submitReportFeedback(sessionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(trainingUserFeedbackRepository);
        }

        @Test
        @DisplayName("Отчёт ещё не сформирован - NotFoundException")
        void throwsWhenReportNotYetGenerated() {
            // given
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            FeedbackRequest request = new FeedbackRequest(TrainingUserFeedback.Vote.UP, List.of(), null);

            // when / then
            assertThatThrownBy(() -> trainingService.submitReportFeedback(sessionId, userId, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Report not found");
            verifyNoInteractions(trainingUserFeedbackRepository);
        }
    }

    @Nested
    @DisplayName("GetReferenceAnswer")
    class GetReferenceAnswer {

        private TrainingQuestion aQuestionWithSession(TrainingSession session, String referenceAnswer) {
            return TrainingQuestion.builder()
                    .id(UUID.randomUUID())
                    .trainingSession(session)
                    .text("Что такое JVM?")
                    .orderIndex(1)
                    .referenceAnswer(referenceAnswer)
                    .build();
        }

        @Test
        @DisplayName("Вопрос не найден - NotFoundException")
        void throwsWhenQuestionNotFound() {
            // given
            UUID questionId = UUID.randomUUID();
            when(trainingQuestionRepository.findWithSessionById(questionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> trainingService.getReferenceAnswer(UUID.randomUUID(), questionId, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Вопрос принадлежит другому пользователю - ForbiddenException")
        void throwsWhenQuestionOwnedByAnotherUser() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, ownerId, PROFESSION);
            TrainingQuestion question = aQuestionWithSession(session, null);
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.getReferenceAnswer(sessionId, question.getId(), UUID.randomUUID()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Access denied");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Вопрос принадлежит другой сессии, чем в запросе - ConflictException")
        void throwsWhenQuestionBelongsToAnotherSession() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aQuestionWithSession(session, null);
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when / then
            assertThatThrownBy(() -> trainingService.getReferenceAnswer(UUID.randomUUID(), question.getId(), userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Invalid session");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Эталонный ответ уже сохранён у вопроса - возвращает его без обращения к LLM")
        void returnsCachedAnswerWithoutCallingLlm() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aQuestionWithSession(session, "Кешированный эталонный ответ");
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));

            // when
            ReferenceAnswerResponse result = trainingService.getReferenceAnswer(sessionId, question.getId(), userId);

            // then
            assertThat(result).isEqualTo(new ReferenceAnswerResponse("Кешированный эталонный ответ"));
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Эталонного ответа ещё нет - запрашивает LLM по навыку/профессии/тексту вопроса, стрипает и сохраняет через writer")
        void generatesAndSavesWhenNoCachedAnswer() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aQuestionWithSession(session, null);
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));
            when(llmService.createReferenceAnswer(any()))
                    .thenReturn(new LlmTrainingReferenceAnswer("  Сгенерированный эталонный ответ  "));

            // when
            ReferenceAnswerResponse result = trainingService.getReferenceAnswer(sessionId, question.getId(), userId);

            // then
            assertThat(result).isEqualTo(new ReferenceAnswerResponse("Сгенерированный эталонный ответ"));

            ArgumentCaptor<LlmTrainingReferenceAnswerRequest> captor =
                    ArgumentCaptor.forClass(LlmTrainingReferenceAnswerRequest.class);
            verify(llmService).createReferenceAnswer(captor.capture());
            assertThat(captor.getValue().skill()).isEqualTo(SKILL);
            assertThat(captor.getValue().profession()).isEqualTo(PROFESSION);
            assertThat(captor.getValue().question()).isEqualTo(question.getText());

            verify(trainingWriter).saveReferenceAnswer(question.getId(), "Сгенерированный эталонный ответ");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   "})
        @DisplayName("LLM вернул null/blank ответ - LlmException, сохранение не происходит")
        void throwsLlmExceptionWhenGeneratedAnswerBlank(String answer) {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            TrainingQuestion question = aQuestionWithSession(session, null);
            when(trainingQuestionRepository.findWithSessionById(question.getId())).thenReturn(Optional.of(question));
            when(llmService.createReferenceAnswer(any())).thenReturn(new LlmTrainingReferenceAnswer(answer));

            // when / then
            assertThatThrownBy(() -> trainingService.getReferenceAnswer(sessionId, question.getId(), userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Reference answer is not available");
            verifyNoInteractions(trainingWriter);
        }
    }

    @Nested
    @DisplayName("CreateReport")
    class CreateReport {

        private LlmTrainingReport usableReport(int casesCount) {
            return new LlmTrainingReport(
                    IntStream.rangeClosed(1, casesCount)
                            .mapToObj(i -> new LlmTrainingCaseReview(i, "Хороший ответ по кейсу " + i, 4))
                            .toList(),
                    "Итоговый фидбэк по тренировке");
        }

        private LlmTrainingReport degenerateReport(String overallFeedback) {
            return new LlmTrainingReport(List.of(), overallFeedback);
        }

        @Test
        @DisplayName("Передаёт skill и profession сессии как есть в LlmTrainingReportRequest")
        void passesSessionSkillAndProfessionAsIsToLlmRequest() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport llmReport = usableReport(3);
            when(llmService.createTrainingReport(any())).thenReturn(llmReport);

            TrainingReportResponse expectedResponse = new TrainingReportResponse(
                    UUID.randomUUID(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.0,
                    llmReport.overallFeedback(), null, List.of());
            when(trainingWriter.completeReport(sessionId, llmReport)).thenReturn(expectedResponse);

            // when
            var result = trainingService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingReportRequest> captor = ArgumentCaptor.forClass(LlmTrainingReportRequest.class);
            verify(llmService).createTrainingReport(captor.capture());
            assertThat(captor.getValue().skill()).isEqualTo(SKILL);
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
            session.setStatus(TrainingSession.Status.COMPLETED);
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(llmService, trainingWriter);
        }

        @Test
        @DisplayName("Отвечено меньше минимума вопросов - ConflictException")
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

            LlmTrainingReport llmReport = usableReport(3);
            when(llmService.createTrainingReport(any())).thenReturn(llmReport);
            when(trainingWriter.completeReport(sessionId, llmReport))
                    .thenThrow(new DataIntegrityViolationException("session already completed concurrently"));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @Test
        @DisplayName("Первый ответ LLM - вырожденный шаблон-заглушка - ретрай возвращает пригодный отчёт, LLM вызван дважды с одинаковым request")
        void retriesReportWhenFirstResponseIsDegenerateTemplate() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport degenerateReport = degenerateReport("string");
            LlmTrainingReport usableReport = usableReport(3);
            when(llmService.createTrainingReport(any())).thenReturn(degenerateReport, usableReport);

            TrainingReportResponse expectedResponse = mock(TrainingReportResponse.class);
            when(trainingWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            var result = trainingService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingReportRequest> captor = ArgumentCaptor.forClass(LlmTrainingReportRequest.class);
            verify(llmService, times(2)).createTrainingReport(captor.capture());
            assertThat(captor.getAllValues()).hasSize(2);
            assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
            verify(trainingWriter).completeReport(sessionId, usableReport);
        }

        @Test
        @DisplayName("Первый ответ LLM пригодный - ретрай не требуется, ровно один вызов")
        void doesNotRetryWhenFirstResponseIsUsable() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport usableReport = usableReport(3);
            when(llmService.createTrainingReport(any())).thenReturn(usableReport);

            TrainingReportResponse expectedResponse = mock(TrainingReportResponse.class);
            when(trainingWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            var result = trainingService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(llmService, times(1)).createTrainingReport(any());
            verify(trainingWriter).completeReport(sessionId, usableReport);
        }

        @Test
        @DisplayName("Оба ответа LLM вырожденные - вызван дважды (не больше), writer-у уходит второй ответ")
        void delegatesSecondDegenerateResponseWhenBothAttemptsAreDegenerate() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            session.setQuestions(List.of(aQuestion(1), aQuestion(2), aQuestion(3)));
            when(trainingSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            LlmTrainingReport firstDegenerate = degenerateReport("string");
            LlmTrainingReport secondDegenerate = degenerateReport("string2");
            when(llmService.createTrainingReport(any())).thenReturn(firstDegenerate, secondDegenerate);
            when(trainingWriter.completeReport(sessionId, secondDegenerate))
                    .thenThrow(new LlmException("Training report has no usable overall feedback"));

            // when / then
            assertThatThrownBy(() -> trainingService.createReport(sessionId, userId))
                    .isInstanceOf(LlmException.class);
            verify(llmService, times(2)).createTrainingReport(any());
            verify(trainingWriter).completeReport(sessionId, secondDegenerate);
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
                    report.getId(), sessionId, SKILL, PROFESSION, TrainingSession.Level.MIDDLE, 4.0, "Фидбэк", null, List.of());
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
    @DisplayName("Restart")
    class Restart {

        @Test
        @DisplayName("Сессия принадлежит пользователю - делегирует writer.restartSession и возвращает его результат")
        void delegatesToWriterWhenSessionOwnedByUser() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.existsByIdAndUserId(sessionId, userId)).thenReturn(true);

            TrainingSessionResponse expectedResponse = mock(TrainingSessionResponse.class);
            when(trainingWriter.restartSession(sessionId)).thenReturn(expectedResponse);

            // when
            var result = trainingService.restart(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(trainingWriter).restartSession(sessionId);
        }

        @Test
        @DisplayName("Сессия не найдена у пользователя - NotFoundException, writer не вызывается")
        void throwsWhenSessionNotOwned() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(trainingSessionRepository.existsByIdAndUserId(sessionId, userId)).thenReturn(false);

            // when / then
            assertThatThrownBy(() -> trainingService.restart(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Session not found");
            verifyNoInteractions(trainingWriter);
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
    @DisplayName("GetOptions")
    class GetOptions {

        @Test
        @DisplayName("Навыки и профессии из словарей маппятся с сохранением порядка, levels и капы как заданы")
        void returnsSkillsAndProfessionsWithLevelsAndCaps() {
            // given
            ProfessionDict first = ProfessionDict.builder().id(UUID.randomUUID()).name("Java-разработчик").build();
            ProfessionDict second = ProfessionDict.builder().id(UUID.randomUUID()).name("Python-разработчик").build();
            when(professionDictRepository.findTop20ByStatusOrderByUsageCountDesc(DictStatus.APPROVED))
                    .thenReturn(List.of(first, second));
            when(skillDictRepository.findTopNames(TrainingService.OPTIONS_LIMIT))
                    .thenReturn(List.of("Spring Boot", "Docker"));

            // when
            TrainingOptionsResponse result = trainingService.getOptions();

            // then
            assertThat(result.skills()).containsExactly("Spring Boot", "Docker");
            assertThat(result.professions()).containsExactly("Java-разработчик", "Python-разработчик");
            assertThat(result.levels()).containsExactly(TrainingSession.Level.values());
            assertThat(result.questionCap()).isEqualTo(TrainingService.QUESTION_CAP);
            assertThat(result.maxQuestions()).isEqualTo(TrainingService.MAX_QUESTIONS);
            assertThat(result.minAnswersToFinish()).isEqualTo(TrainingService.MIN_ANSWERED_TO_FINISH);
        }

        @Test
        @DisplayName("Пустые словари - пустые списки skills/professions, а не ошибка")
        void returnsEmptyResultsWhenDictionariesEmpty() {
            // given
            when(professionDictRepository.findTop20ByStatusOrderByUsageCountDesc(DictStatus.APPROVED))
                    .thenReturn(List.of());
            when(skillDictRepository.findTopNames(TrainingService.OPTIONS_LIMIT)).thenReturn(List.of());

            // when
            TrainingOptionsResponse result = trainingService.getOptions();

            // then
            assertThat(result.professions()).isEmpty();
            assertThat(result.skills()).isEmpty();
        }
    }

    @Nested
    @DisplayName("NormalizeInput")
    class NormalizeInput {

        @Test
        @DisplayName("Стрипает поля в LLM-запросе, собирает ответ из полей LLM-ответа")
        void stripsFieldsInLlmRequestAndMapsResponse() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest("  джава дев  ", " спринг ");
            LlmInputNormalization llmResponse = new LlmInputNormalization(
                    true, List.of("Spring"), true, List.of("Java-разработчик"), true);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            ArgumentCaptor<LlmInputNormalizationRequest> captor =
                    ArgumentCaptor.forClass(LlmInputNormalizationRequest.class);
            verify(llmService).normalizeInput(captor.capture());
            assertThat(captor.getValue().skill()).isEqualTo("джава дев");
            assertThat(captor.getValue().profession()).isEqualTo("спринг");

            assertThat(result.skillRecognized()).isTrue();
            assertThat(result.skillSuggestions()).containsExactly("Spring");
            assertThat(result.professionRecognized()).isTrue();
            assertThat(result.professionSuggestions()).containsExactly("Java-разработчик");
            assertThat(result.skillFitsProfession()).isTrue();
        }

        @Test
        @DisplayName("LLM вернул null-списки подсказок - в ответе пустые списки, а не null")
        void nullSuggestionListsFromLlmBecomeEmptyLists() {
            // given
            NormalizeInputRequest request = new NormalizeInputRequest(SKILL, PROFESSION);
            LlmInputNormalization llmResponse = new LlmInputNormalization(false, null, false, null, false);
            when(llmService.normalizeInput(any())).thenReturn(llmResponse);

            // when
            NormalizeInputResponse result = trainingService.normalizeInput(request);

            // then
            assertThat(result.skillSuggestions()).isNotNull().isEmpty();
            assertThat(result.professionSuggestions()).isNotNull().isEmpty();
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
    @DisplayName("SuggestSkills")
    class SuggestSkills {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"j", " j "})
        @DisplayName("Запрос короче 2 символов (null, 1 символ, 1 символ после strip) - пустой список без обращения к репозиторию")
        void returnsEmptyListForTooShortQuery(String query) {
            // when
            List<String> result = trainingService.suggestSkills(PROFESSION, query);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(skillDictRepository);
        }

        @Test
        @DisplayName("Профессия указана - ищет в её словаре, маппит найденные записи в имена, обрезает пробелы по краям профессии")
        void mapsSuggestedSkillsToNamesPreservingOrderWithProfession() {
            // given
            SkillDict first = SkillDict.builder().id(UUID.randomUUID()).name("Spring Boot").build();
            SkillDict second = SkillDict.builder().id(UUID.randomUUID()).name("Spring Security").build();
            when(skillDictRepository.suggest(DictText.matchKey(PROFESSION), "sp", TrainingService.SUGGEST_LIMIT))
                    .thenReturn(List.of(first, second));

            // when
            List<String> result = trainingService.suggestSkills("  " + PROFESSION + "  ", "sp");

            // then
            assertThat(result).containsExactly("Spring Boot", "Spring Security");
        }

        @Test
        @DisplayName("Профессия введена другими словами и в другом регистре - в репозиторий уходит её ключ сравнения, а не введённый текст")
        void queriesByProfessionMatchKeyRegardlessOfInputWording() {
            // given
            when(skillDictRepository.suggest("java разработчик", "sp", TrainingService.SUGGEST_LIMIT))
                    .thenReturn(List.of());

            // when
            List<String> result = trainingService.suggestSkills("разработчик на JAVA", "sp");

            // then
            assertThat(result).isEmpty();
            verify(skillDictRepository).suggest("java разработчик", "sp", TrainingService.SUGGEST_LIMIT);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"  "})
        @DisplayName("Профессия null или пробельная - подсказки собираются по всему словарю (suggestAcrossProfessions), а не по одной профессии")
        void returnsNamesAcrossProfessionsWhenProfessionBlankOrNull(String profession) {
            // given
            when(skillDictRepository.suggestAcrossProfessions("sp", TrainingService.SUGGEST_LIMIT))
                    .thenReturn(List.of("Spring Boot", "Spring Security"));

            // when
            List<String> result = trainingService.suggestSkills(profession, "sp");

            // then
            assertThat(result).containsExactly("Spring Boot", "Spring Security");
            verify(skillDictRepository, never()).suggest(any(), any(), anyInt());
        }
    }
}
