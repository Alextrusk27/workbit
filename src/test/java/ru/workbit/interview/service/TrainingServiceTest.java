package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.content.model.ProfessionDict;
import ru.workbit.content.repository.ProfessionDictRepository;
import ru.workbit.interview.dto.CreateSessionRequest;
import ru.workbit.interview.dto.TrainingOptionsResponse;
import ru.workbit.interview.dto.TrainingQuestionResponse;
import ru.workbit.interview.dto.TrainingReportResponse;
import ru.workbit.interview.dto.TrainingSessionResponse;
import ru.workbit.interview.model.Level;
import ru.workbit.interview.model.SessionStatus;
import ru.workbit.interview.model.TrainingQuestion;
import ru.workbit.interview.model.TrainingSession;
import ru.workbit.interview.model.mapper.TrainingQuestionMapper;
import ru.workbit.interview.model.mapper.TrainingReportMapper;
import ru.workbit.interview.model.mapper.TrainingSessionMapper;
import ru.workbit.interview.repository.TrainingQuestionRepository;
import ru.workbit.interview.repository.TrainingSessionRepository;
import ru.workbit.llm.dto.LlmTrainingQuestion;
import ru.workbit.llm.dto.LlmTrainingQuestionRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.llm.service.LlmService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("Сохраняет сессию с профессией и темой из запроса, возвращает answeredCount=0")
        void savesSessionWithProfessionAndTopicFromRequest() {
            // given
            UUID userId = UUID.randomUUID();
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, TOPIC, Level.MIDDLE);
            TrainingSession mappedEntity = TrainingSession.builder()
                    .profession(PROFESSION)
                    .topic(TOPIC)
                    .level(Level.MIDDLE)
                    .build();
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, TOPIC, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(mappedEntity, 0)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
            verify(trainingSessionRepository).save(captor.capture());
            TrainingSession saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getProfession()).isEqualTo(PROFESSION);
            assertThat(saved.getTopic()).isEqualTo(TOPIC);
        }

        @Test
        @DisplayName("Создаёт сессию без темы - topic сохраняется как null")
        void createsSessionWithNullTopic() {
            // given
            UUID userId = UUID.randomUUID();
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, null, Level.MIDDLE);
            TrainingSession mappedEntity = TrainingSession.builder()
                    .profession(PROFESSION)
                    .topic(null)
                    .level(Level.MIDDLE)
                    .build();
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, null, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(mappedEntity, 0)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
            verify(trainingSessionRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isNull();
        }

        @Test
        @DisplayName("Пробельная тема в запросе - topic нормализуется в null перед сохранением")
        void normalizesBlankTopicToNull() {
            // given
            UUID userId = UUID.randomUUID();
            CreateSessionRequest request = new CreateSessionRequest(PROFESSION, "   ", Level.MIDDLE);
            TrainingSession mappedEntity = TrainingSession.builder()
                    .profession(PROFESSION)
                    .topic("   ")
                    .level(Level.MIDDLE)
                    .build();
            when(trainingSessionMapper.toEntity(request)).thenReturn(mappedEntity);

            TrainingSessionResponse expectedResponse = new TrainingSessionResponse(
                    null, PROFESSION, null, Level.MIDDLE, SessionStatus.CREATED, 0, null, null);
            when(trainingSessionMapper.toResponse(mappedEntity, 0)).thenReturn(expectedResponse);

            // when
            var result = trainingService.create(request, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
            verify(trainingSessionRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isNull();
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

        @Test
        @DisplayName("Передаёт profession сессии как есть в LlmTrainingQuestionRequest")
        void passesSessionProfessionAsIsToLlmRequest() {
            // given
            UUID sessionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            TrainingSession session = aSession(sessionId, userId, PROFESSION);
            when(trainingSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
            when(trainingQuestionRepository.findNextUnanswered(sessionId)).thenReturn(Optional.empty());
            when(trainingQuestionRepository.countByTrainingSessionIdAndFollowUpFalseAndAnsweredTrue(sessionId))
                    .thenReturn(0L);
            when(trainingQuestionRepository.findAllByTrainingSessionIdOrderByOrderIndex(sessionId))
                    .thenReturn(List.of());

            LlmTrainingQuestion generated = new LlmTrainingQuestion("Расскажите про Spring Boot", "MAIN");
            when(llmService.generateTrainingQuestion(any())).thenReturn(generated);

            TrainingQuestionResponse expectedResponse = new TrainingQuestionResponse(
                    UUID.randomUUID(), 1, generated.question(), false, null, null, null);
            when(trainingWriter.saveQuestion(sessionId, generated.question(), false)).thenReturn(expectedResponse);

            // when
            var result = trainingService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmTrainingQuestionRequest> captor = ArgumentCaptor.forClass(LlmTrainingQuestionRequest.class);
            verify(llmService).generateTrainingQuestion(captor.capture());
            assertThat(captor.getValue().profession()).isEqualTo(PROFESSION);
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
    }
}
