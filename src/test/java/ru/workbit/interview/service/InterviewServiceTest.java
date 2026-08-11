package ru.workbit.interview.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.workbit.exception.ConflictException;
import ru.workbit.exception.ForbiddenException;
import ru.workbit.exception.LlmException;
import ru.workbit.exception.NotFoundException;
import ru.workbit.interview.dto.InterviewQuestionResponse;
import ru.workbit.interview.dto.InterviewReportResponse;
import ru.workbit.interview.dto.InterviewSessionResponse;
import ru.workbit.interview.dto.SubmitAnswerRequest;
import ru.workbit.interview.model.InterviewQuestion;
import ru.workbit.interview.model.InterviewReport;
import ru.workbit.interview.model.InterviewSession;
import ru.workbit.interview.model.mapper.InterviewQuestionMapper;
import ru.workbit.interview.model.mapper.InterviewReportMapper;
import ru.workbit.interview.model.mapper.InterviewSessionMapper;
import ru.workbit.interview.repository.InterviewQuestionRepository;
import ru.workbit.interview.repository.InterviewSessionRepository;
import ru.workbit.llm.dto.LlmInterviewAnswer;
import ru.workbit.llm.dto.LlmInterviewAnswerReview;
import ru.workbit.llm.dto.LlmInterviewFollowUp;
import ru.workbit.llm.dto.LlmInterviewFollowUpDecision;
import ru.workbit.llm.dto.LlmInterviewFollowUpRequest;
import ru.workbit.llm.dto.LlmInterviewQuestions;
import ru.workbit.llm.dto.LlmInterviewQuestionsRequest;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewReportRequest;
import ru.workbit.llm.service.LlmService;
import ru.workbit.vacancy.dto.VacancyData;
import ru.workbit.vacancy.dto.VacancySnapshotView;
import ru.workbit.vacancy.model.VacancySnapshot;
import ru.workbit.vacancy.service.VacancyService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewServiceTest")
class InterviewServiceTest {

    @Mock
    InterviewSessionRepository interviewSessionRepository;
    @Mock
    InterviewQuestionRepository interviewQuestionRepository;
    @Mock
    InterviewWriter interviewWriter;
    @Mock
    VacancyService vacancyService;
    @Mock
    LlmService llmService;
    @Mock
    InterviewSessionMapper interviewSessionMapper;
    @Mock
    InterviewQuestionMapper interviewQuestionMapper;
    @Mock
    InterviewReportMapper interviewReportMapper;

    @InjectMocks
    InterviewService interviewService;

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
                .answered(answered)
                .text(text)
                .answerText(answerText)
                .build();
    }

    private static VacancyData aVacancyData(String experience) {
        return new VacancyData(VacancySnapshot.Source.HH, "123", "https://hh.ru/vacancy/123",
                "Java-разработчик", "ООО Ромашка", experience, List.of("Java", "Spring"), "Описание вакансии");
    }

    private static VacancySnapshotView aVacancySnapshotView(String experience) {
        return new VacancySnapshotView("123", "Java-разработчик", "ООО Ромашка", "https://hh.ru/vacancy/123", experience);
    }

    @Nested
    @DisplayName("CreateSession")
    class CreateSession {

        private final UUID userId = UUID.randomUUID();
        private final String vacancyUrl = "https://hh.ru/vacancy/123";

        @Test
        @DisplayName("Вопросов ровно MIN_COUNT - сессия создаётся, запрос к LLM собран из полей вакансии")
        void createsSessionWithExactlyMinCountQuestions() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewQuestionsRequest expectedRequest = new LlmInterviewQuestionsRequest(
                    vacancyData.name(), vacancyData.employer(), vacancyData.keySkills(), vacancyData.description(),
                    LlmInterviewQuestionsRequest.MIN_COUNT, LlmInterviewQuestionsRequest.MAX_COUNT);
            List<String> questions = List.of("В1", "В2", "В3", "В4", "В5");
            when(llmService.generateInterviewQuestions(vacancyData.experience(), expectedRequest))
                    .thenReturn(new LlmInterviewQuestions(questions));

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), 5);
            when(interviewWriter.createSession(vacancyData, userId, questions)).thenReturn(createdSession);

            InterviewSessionResponse expectedResponse = new InterviewSessionResponse(
                    createdSession.getId(), vacancyData.sourceId(), vacancyData.name(), vacancyData.employer(),
                    vacancyData.url(), vacancyData.experience(), InterviewSession.Status.CREATED, 0, 5, null, null);
            when(interviewSessionMapper.toResponse(createdSession, vacancyData, 0)).thenReturn(expectedResponse);

            // when
            InterviewSessionResponse result = interviewService.createSession(vacancyUrl, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(interviewWriter).createSession(vacancyData, userId, questions);
            verify(llmService, times(1)).generateInterviewQuestions(vacancyData.experience(), expectedRequest);
        }

        @Test
        @DisplayName("LLM вернул больше MAX_COUNT вопросов - обрезается до MAX_COUNT, лишние не уходят в writer")
        void truncatesToMaxCountWhenLlmReturnsMore() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            List<String> raw = IntStream.rangeClosed(1, 25).mapToObj(i -> "Вопрос " + i).toList();
            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(new LlmInterviewQuestions(raw));

            List<String> expectedQuestions = IntStream.rangeClosed(1, LlmInterviewQuestionsRequest.MAX_COUNT)
                    .mapToObj(i -> "Вопрос " + i).toList();
            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), expectedQuestions.size());
            when(interviewWriter.createSession(vacancyData, userId, expectedQuestions)).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(eq(createdSession), eq(vacancyData), eq(0)))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewWriter).createSession(vacancyData, userId, expectedQuestions);
        }

        @Test
        @DisplayName("LLM вернул null/blank вперемешку - фильтруются, в writer уходят только годные")
        void filtersNullAndBlankQuestions() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(new LlmInterviewQuestions(
                    Arrays.asList(null, "   ", "Годный 1", "", "Годный 2", "Годный 3", "Годный 4", "Годный 5")));

            List<String> expectedQuestions = List.of("Годный 1", "Годный 2", "Годный 3", "Годный 4", "Годный 5");
            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), expectedQuestions.size());
            when(interviewWriter.createSession(vacancyData, userId, expectedQuestions)).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(eq(createdSession), eq(vacancyData), eq(0)))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewWriter).createSession(vacancyData, userId, expectedQuestions);
        }

        @Test
        @DisplayName("LLM вернул null-список вопросов - LlmException, сессия не создаётся")
        void throwsWhenLlmReturnsNullQuestionsList() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(new LlmInterviewQuestions(null));

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for an interview session");
            verify(interviewWriter, never()).createSession(any(), any(), any());
            verifyNoInteractions(interviewSessionMapper);
        }

        @Test
        @DisplayName("Годных вопросов меньше MIN_COUNT - LlmException, сессия не создаётся")
        void throwsWhenUsableQuestionsBelowMinCount() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(llmService.generateInterviewQuestions(any(), any()))
                    .thenReturn(new LlmInterviewQuestions(List.of("В1", "В2", "В3", "В4")));

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for an interview session");
            verify(interviewWriter, never()).createSession(any(), any(), any());
            verifyNoInteractions(interviewSessionMapper);
        }

        @Test
        @DisplayName("Первый вызов вернул меньше MIN_COUNT, ретрай вернул достаточно - сессия создаётся из вопросов ретрая, LLM вызван дважды")
        void retriesAndCreatesSessionWhenRetrySucceeds() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);

            LlmInterviewQuestions firstAttempt = new LlmInterviewQuestions(List.of("В1", "В2", "В3", "В4"));
            List<String> retryQuestions = List.of("П1", "П2", "П3", "П4", "П5");
            LlmInterviewQuestions secondAttempt = new LlmInterviewQuestions(retryQuestions);
            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(firstAttempt, secondAttempt);

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), retryQuestions.size());
            when(interviewWriter.createSession(vacancyData, userId, retryQuestions)).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(eq(createdSession), eq(vacancyData), eq(0)))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(llmService, times(2)).generateInterviewQuestions(any(), any());
            verify(interviewWriter).createSession(vacancyData, userId, retryQuestions);
        }

        @Test
        @DisplayName("Оба вызова вернули меньше MIN_COUNT - LlmException, LLM вызван дважды (не больше), сессия не создаётся")
        void throwsAfterRetryWhenBothAttemptsBelowMinCount() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(llmService.generateInterviewQuestions(any(), any()))
                    .thenReturn(new LlmInterviewQuestions(List.of("В1", "В2", "В3", "В4")));

            // when / then
            assertThatThrownBy(() -> interviewService.createSession(vacancyUrl, userId))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Not enough questions for an interview session");
            verify(llmService, times(2)).generateInterviewQuestions(any(), any());
            verify(interviewWriter, never()).createSession(any(), any(), any());
            verifyNoInteractions(interviewSessionMapper);
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

            List<String> questions = List.of("В1", "В2", "В3", "В4", "В5");
            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(new LlmInterviewQuestions(questions));

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), questions.size());
            when(interviewWriter.createSession(vacancyData, userId, questions)).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(eq(createdSession), eq(vacancyData), eq(0)))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewWriter).createSession(vacancyData, userId, questions);
            verify(llmService, times(1)).generateInterviewQuestions(any(), any());
        }

        @Test
        @DisplayName("Снапшотов по sourceId нет - exists не вызывается, сессия создаётся штатно")
        void createsSessionWhenNoSnapshotsForVacancy() {
            // given
            VacancyData vacancyData = aVacancyData("От 1 года до 3 лет");
            when(vacancyService.fetch(vacancyUrl)).thenReturn(vacancyData);
            when(vacancyService.getSnapshotIds(vacancyData.sourceId())).thenReturn(List.of());

            List<String> questions = List.of("В1", "В2", "В3", "В4", "В5");
            when(llmService.generateInterviewQuestions(any(), any())).thenReturn(new LlmInterviewQuestions(questions));

            InterviewSession createdSession = aSession(UUID.randomUUID(), userId, InterviewSession.Status.CREATED,
                    UUID.randomUUID(), questions.size());
            when(interviewWriter.createSession(vacancyData, userId, questions)).thenReturn(createdSession);
            when(interviewSessionMapper.toResponse(eq(createdSession), eq(vacancyData), eq(0)))
                    .thenReturn(mock(InterviewSessionResponse.class));

            // when
            interviewService.createSession(vacancyUrl, userId);

            // then
            verify(interviewSessionRepository, never())
                    .existsByUserIdAndVacancySnapshotIdInAndStatusNot(any(), any(), any());
            verify(interviewWriter).createSession(vacancyData, userId, questions);
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

        private InterviewSession activeSession() {
            return aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS, vacancySnapshotId, 10);
        }

        private static Stream<Arguments> nonFollowUpDecisions() {
            return Stream.of(
                    Arguments.of(false, null),
                    Arguments.of(true, null),
                    Arguments.of(true, "   "));
        }

        @Test
        @DisplayName("Сессия не найдена - NotFoundException, зависимости ниже не дёргаются")
        void throwsWhenSessionNotFound() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

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
                    vacancySnapshotId, 10);
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
            verifyNoInteractions(interviewQuestionRepository, llmService, vacancyService, interviewWriter, interviewQuestionMapper);
        }

        @Test
        @DisplayName("Есть неотвеченный follow-up - возвращается он, приоритет выше решения LLM")
        void returnsPendingFollowUpWhenPresent() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            InterviewQuestion pendingFollowUp = aQuestion(UUID.randomUUID(), UUID.randomUUID(), 1,
                    true, false, "Уточняющий вопрос", null);
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.of(pendingFollowUp));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(pendingFollowUp)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llmService, vacancyService, interviewWriter);
            verify(interviewQuestionRepository, never()).findLastAnsweredWithoutFollowUpCheck(any());
        }

        @Test
        @DisplayName("Нет неотвеченного follow-up и нет непроверенного последнего ответа - возвращается очередной основной вопрос")
        void returnsMainQuestionWhenNoLastAnsweredWithoutFollowUpCheck() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.empty());
            InterviewQuestion main = aQuestion(UUID.randomUUID(), null, 4, false, false, "Основной вопрос", null);
            when(interviewQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(main));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(main)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llmService, vacancyService, interviewWriter);
        }

        @Test
        @DisplayName("Последний ответ - на основной вопрос, лимит уточнений не исчерпан, LLM одобряет - возвращается новый follow-up")
        void returnsGeneratedFollowUpWhenDecisionApproves() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            InterviewQuestion answered = aQuestion(UUID.randomUUID(), null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId())).thenReturn(List.of());

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewFollowUpDecision decision = new LlmInterviewFollowUpDecision(true, "Сгенерированный follow-up");
            when(llmService.decideInterviewFollowUp(eq(vacancy.experience()), any())).thenReturn(decision);

            InterviewQuestionResponse followUpResponse = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveFollowUp(answered.getId(), answered.getId(), decision.question()))
                    .thenReturn(followUpResponse);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(followUpResponse);

            ArgumentCaptor<LlmInterviewFollowUpRequest> captor = ArgumentCaptor.forClass(LlmInterviewFollowUpRequest.class);
            verify(llmService).decideInterviewFollowUp(eq(vacancy.experience()), captor.capture());
            assertThat(captor.getValue()).isEqualTo(new LlmInterviewFollowUpRequest(
                    vacancy.name(), answered.getText(), answered.getAnswerText(), List.of()));

            verify(interviewWriter, never()).markFollowUpChecked(any());
            verify(interviewQuestionRepository, never()).findNextUnansweredMain(any());
        }

        @Test
        @DisplayName("Последний ответ - на follow-up: caseMainId и текст решения берутся из основного вопроса-родителя")
        void usesParentQuestionWhenLastAnsweredIsFollowUp() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            UUID mainId = UUID.randomUUID();
            InterviewQuestion mainQuestion = aQuestion(mainId, null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            InterviewQuestion answeredFollowUp = aQuestion(UUID.randomUUID(), mainId, 1, true, true,
                    "А это как связано с DI?", "Через инверсию контроля");
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId))
                    .thenReturn(Optional.of(answeredFollowUp));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(mainId))
                    .thenReturn(List.of(answeredFollowUp));
            when(interviewQuestionRepository.findById(mainId)).thenReturn(Optional.of(mainQuestion));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);

            LlmInterviewFollowUpDecision decision = new LlmInterviewFollowUpDecision(true, "Ещё один уточняющий");
            when(llmService.decideInterviewFollowUp(eq(vacancy.experience()), any())).thenReturn(decision);

            InterviewQuestionResponse followUpResponse = mock(InterviewQuestionResponse.class);
            when(interviewWriter.saveFollowUp(answeredFollowUp.getId(), mainId, decision.question()))
                    .thenReturn(followUpResponse);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(followUpResponse);
            verify(interviewQuestionRepository).findById(mainId);

            ArgumentCaptor<LlmInterviewFollowUpRequest> captor = ArgumentCaptor.forClass(LlmInterviewFollowUpRequest.class);
            verify(llmService).decideInterviewFollowUp(eq(vacancy.experience()), captor.capture());
            assertThat(captor.getValue()).isEqualTo(new LlmInterviewFollowUpRequest(
                    vacancy.name(), mainQuestion.getText(), mainQuestion.getAnswerText(),
                    List.of(new LlmInterviewFollowUp(answeredFollowUp.getText(), answeredFollowUp.getAnswerText()))));
        }

        @Test
        @DisplayName("Последний ответ - на follow-up, но основной вопрос-родитель не найден - NotFoundException")
        void throwsNotFoundWhenCaseMainMissingForFollowUpBranch() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            UUID mainId = UUID.randomUUID();
            InterviewQuestion answeredFollowUp = aQuestion(UUID.randomUUID(), mainId, 1, true, true,
                    "А это как связано с DI?", "Через инверсию контроля");
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId))
                    .thenReturn(Optional.of(answeredFollowUp));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(mainId))
                    .thenReturn(List.of(answeredFollowUp));
            when(interviewQuestionRepository.findById(mainId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Question not found");
            verifyNoInteractions(vacancyService, llmService, interviewWriter);
        }

        @Test
        @DisplayName("Лимит уточнений по кейсу исчерпан - follow-up помечается проверенным, возвращается основной вопрос")
        void marksCheckedAndFallsToMainWhenFollowUpLimitReached() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            InterviewQuestion answered = aQuestion(UUID.randomUUID(), null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId())).thenReturn(List.of(
                    aQuestion(UUID.randomUUID(), answered.getId(), 1, true, true, "Уточнение 1", "Ответ 1"),
                    aQuestion(UUID.randomUUID(), answered.getId(), 2, true, true, "Уточнение 2", "Ответ 2")));

            InterviewQuestion main = aQuestion(UUID.randomUUID(), null, 4, false, false, "Основной вопрос", null);
            when(interviewQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(main));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(main)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewWriter).markFollowUpChecked(answered.getId());
            verifyNoInteractions(vacancyService, llmService);
        }

        @ParameterizedTest
        @MethodSource("nonFollowUpDecisions")
        @DisplayName("LLM не одобряет follow-up (askFollowUp=false либо question null/blank) - помечается проверенным, возвращается основной вопрос")
        void marksCheckedAndFallsToMainWhenDecisionDoesNotProduceFollowUp(boolean askFollowUp, String question) {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());

            InterviewQuestion answered = aQuestion(UUID.randomUUID(), null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId())).thenReturn(List.of());

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            when(llmService.decideInterviewFollowUp(eq(vacancy.experience()), any()))
                    .thenReturn(new LlmInterviewFollowUpDecision(askFollowUp, question));

            InterviewQuestion main = aQuestion(UUID.randomUUID(), null, 4, false, false, "Основной вопрос", null);
            when(interviewQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(main));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(main)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewWriter).markFollowUpChecked(answered.getId());
            verify(interviewWriter, never()).saveFollowUp(any(), any(), any());
        }

        @Test
        @DisplayName("Конкурентная гонка при сохранении follow-up - уже созданный уточняющий вопрос возвращается без дублирования")
        void resolvesRaceByReturningExistingFollowUpAfterDataIntegrityViolation() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));

            InterviewQuestion answered = aQuestion(UUID.randomUUID(), null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            InterviewQuestion raceFollowUp = aQuestion(UUID.randomUUID(), answered.getId(), 1, true, false,
                    "Уточнение из параллельного запроса", null);
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId))
                    .thenReturn(Optional.empty(), Optional.of(raceFollowUp));
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId())).thenReturn(List.of());

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            LlmInterviewFollowUpDecision decision = new LlmInterviewFollowUpDecision(true, "Новый уточняющий");
            when(llmService.decideInterviewFollowUp(eq(vacancy.experience()), any())).thenReturn(decision);
            when(interviewWriter.saveFollowUp(answered.getId(), answered.getId(), decision.question()))
                    .thenThrow(new DataIntegrityViolationException("duplicate follow-up"));

            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(raceFollowUp)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewQuestionRepository, times(2)).findNextUnansweredFollowUp(sessionId);
            verify(interviewQuestionRepository, never()).findNextUnansweredMain(any());
        }

        @Test
        @DisplayName("Конкурентная гонка при сохранении follow-up, но конкурент его не создал - переход к основному вопросу")
        void resolvesRaceByFallingBackToMainWhenNoFollowUpFoundAfterConflict() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));

            InterviewQuestion answered = aQuestion(UUID.randomUUID(), null, 3, false, true,
                    "Расскажите про SOLID", "Мой ответ про SOLID");
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.of(answered));
            when(interviewQuestionRepository.findAllByParentQuestionIdOrderByOrderIndex(answered.getId())).thenReturn(List.of());

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            LlmInterviewFollowUpDecision decision = new LlmInterviewFollowUpDecision(true, "Новый уточняющий");
            when(llmService.decideInterviewFollowUp(eq(vacancy.experience()), any())).thenReturn(decision);
            when(interviewWriter.saveFollowUp(answered.getId(), answered.getId(), decision.question()))
                    .thenThrow(new DataIntegrityViolationException("duplicate follow-up"));

            InterviewQuestion main = aQuestion(UUID.randomUUID(), null, 4, false, false, "Основной вопрос", null);
            when(interviewQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.of(main));
            InterviewQuestionResponse expected = mock(InterviewQuestionResponse.class);
            when(interviewQuestionMapper.toDto(main)).thenReturn(expected);

            // when
            InterviewQuestionResponse result = interviewService.nextQuestion(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewQuestionRepository, times(2)).findNextUnansweredFollowUp(sessionId);
        }

        @Test
        @DisplayName("Нет ни follow-up, ни основных вопросов - ConflictException")
        void throwsConflictWhenNoQuestionsLeft() {
            // given
            when(interviewSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(activeSession()));
            when(interviewQuestionRepository.findNextUnansweredFollowUp(sessionId)).thenReturn(Optional.empty());
            when(interviewQuestionRepository.findLastAnsweredWithoutFollowUpCheck(sessionId)).thenReturn(Optional.empty());
            when(interviewQuestionRepository.findNextUnansweredMain(sessionId)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> interviewService.nextQuestion(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("No questions left");
            verifyNoInteractions(llmService, vacancyService, interviewWriter, interviewQuestionMapper);
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
        @DisplayName("Сессия уже завершена - ConflictException")
        void throwsWhenSessionAlreadyCompleted() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    vacancySnapshotId, 2);
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
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
                    "Средняя", "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(eq(vacancy.experience()), any())).thenReturn(llmReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, llmReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmInterviewReportRequest> captor = ArgumentCaptor.forClass(LlmInterviewReportRequest.class);
            verify(llmService, times(1)).createInterviewReport(eq(vacancy.experience()), captor.capture());
            assertThat(captor.getValue()).isEqualTo(new LlmInterviewReportRequest(
                    vacancy.name(), vacancy.experience(), List.of(
                            new LlmInterviewAnswer(1, main1.getText(), main1.getAnswerText(),
                                    List.of(new LlmInterviewFollowUp(followUp1.getText(), followUp1.getAnswerText()))),
                            new LlmInterviewAnswer(2, main2.getText(), main2.getAnswerText(), List.of()))));
        }

        @Test
        @DisplayName("Конкурентное завершение сессии - ConflictException вместо DataIntegrityViolationException")
        void throwsConflictWhenWriterDetectsConcurrentCompletion() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.IN_PROGRESS,
                    vacancySnapshotId, 1);
            session.setQuestions(List.of(aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1")));
            when(interviewSessionRepository.findWithQuestionsById(sessionId)).thenReturn(Optional.of(session));

            VacancySnapshotView vacancy = aVacancySnapshotView("От 1 года до 3 лет");
            when(vacancyService.getSnapshotView(vacancySnapshotId)).thenReturn(vacancy);
            LlmInterviewReport llmReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ", 4)),
                    "Средняя", "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(eq(vacancy.experience()), any())).thenReturn(llmReport);
            when(interviewWriter.completeReport(sessionId, llmReport))
                    .thenThrow(new DataIntegrityViolationException("already completed"));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Session already finished");
        }

        @Test
        @DisplayName("Первый ответ LLM - вырожденный шаблон-заглушка - ретрай возвращает пригодный отчёт, LLM вызван дважды с одинаковым request")
        void retriesReportWhenFirstResponseIsDegenerateTemplate() {
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
                    List.of(new LlmInterviewAnswerReview(1, "string", 3)), "string", "string", null, null);
            LlmInterviewReport usableReport = new LlmInterviewReport(
                    List.of(new LlmInterviewAnswerReview(1, "Хороший ответ по первому кейсу", 4),
                            new LlmInterviewAnswerReview(2, "Хороший ответ по второму кейсу", 5)),
                    "Средняя", "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(eq(vacancy.experience()), any()))
                    .thenReturn(degenerateReport, usableReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);

            ArgumentCaptor<LlmInterviewReportRequest> captor = ArgumentCaptor.forClass(LlmInterviewReportRequest.class);
            verify(llmService, times(2)).createInterviewReport(eq(vacancy.experience()), captor.capture());
            assertThat(captor.getAllValues()).hasSize(2);
            assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
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
                    "Средняя", "Итоговый фидбэк по интервью", null, null);
            when(llmService.createInterviewReport(eq(vacancy.experience()), any())).thenReturn(usableReport);

            InterviewReportResponse expectedResponse = mock(InterviewReportResponse.class);
            when(interviewWriter.completeReport(sessionId, usableReport)).thenReturn(expectedResponse);

            // when
            InterviewReportResponse result = interviewService.createReport(sessionId, userId);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            verify(llmService, times(1)).createInterviewReport(eq(vacancy.experience()), any());
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

            LlmInterviewReport firstDegenerate = new LlmInterviewReport(List.of(), "string", "string", null, null);
            LlmInterviewReport secondDegenerate = new LlmInterviewReport(List.of(), "string", "string2", null, null);
            when(llmService.createInterviewReport(eq(vacancy.experience()), any()))
                    .thenReturn(firstDegenerate, secondDegenerate);
            when(interviewWriter.completeReport(sessionId, secondDegenerate))
                    .thenThrow(new LlmException("Interview report has no usable overall feedback"));

            // when / then
            assertThatThrownBy(() -> interviewService.createReport(sessionId, userId))
                    .isInstanceOf(LlmException.class);
            verify(llmService, times(2)).createInterviewReport(eq(vacancy.experience()), any());
            verify(interviewWriter).completeReport(sessionId, secondDegenerate);
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
        @DisplayName("Отчёт сформирован - маппится только с отвеченными вопросами, отсортированными по orderIndex")
        void returnsMappedReportWithAnsweredQuestionsOnly() {
            // given
            InterviewSession session = aSession(sessionId, userId, InterviewSession.Status.COMPLETED,
                    UUID.randomUUID(), 2);
            InterviewQuestion answeredSecond = aQuestion(UUID.randomUUID(), null, 2, false, true, "Вопрос 2", "Ответ 2");
            InterviewQuestion answeredFirst = aQuestion(UUID.randomUUID(), null, 1, false, true, "Вопрос 1", "Ответ 1");
            InterviewQuestion unanswered = aQuestion(UUID.randomUUID(), null, 3, false, false, "Вопрос 3", null);
            session.setQuestions(List.of(answeredSecond, answeredFirst, unanswered));
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
