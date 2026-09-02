package ru.workbit.llm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.llm.client.InterviewerClient;
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.dto.*;
import ru.workbit.training.model.TrainingSession;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmServiceTest")
class LlmServiceTest {

    @Mock
    LlmClient llm;

    @Mock
    InterviewerClient interviewer;

    @InjectMocks
    LlmService llmService;

    private static Stream<Arguments> experienceGrades() {
        return Stream.of(
                Arguments.of("Нет опыта", "exp0"),
                Arguments.of("От 1 года до 3 лет", "exp1"),
                Arguments.of("От 3 до 6 лет", "exp3"),
                Arguments.of("Более 6 лет", "exp6"),
                Arguments.of("", "exp1"),
                Arguments.of(null, "exp1"),
                Arguments.of("Неизвестная категория опыта", "exp1")
        );
    }

    @Nested
    @DisplayName("GenerateTrainingQuestions")
    class GenerateTrainingQuestions {

        @ParameterizedTest(name = "уровень {0}")
        @EnumSource(TrainingSession.Level.class)
        @DisplayName("Роутит вызов на агента training-question-generator-{грейд} по уровню сессии")
        void routesByLevelGrade(TrainingSession.Level level) {
            // given
            var grade = level.getGrade();
            var request = new LlmTrainingQuestionsRequest("Spring Boot", "Java-разработчик", 5, List.of());
            var expected = new LlmTrainingQuestions(List.of("Что такое JVM?"));
            when(llm.call(anyString(), eq(request), eq(LlmTrainingQuestions.class))).thenReturn(expected);

            // when
            var result = llmService.generateTrainingQuestions(grade, request);

            // then
            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<String> agentKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(llm).call(agentKeyCaptor.capture(), eq(request), eq(LlmTrainingQuestions.class));
            assertThat(agentKeyCaptor.getValue()).isEqualTo("training-question-generator-" + grade);
        }
    }

    @Nested
    @DisplayName("CreateTrainingReport")
    class CreateTrainingReport {

        @Test
        @DisplayName("Вызывает агента training-reviewer с запросом одной переменной JSON_STRING, а не полями DTO")
        void callsTrainingReviewerAgentWithJsonStringVariable() {
            // given
            var request = new LlmTrainingReportRequest("Spring Boot", "Java-разработчик", List.of());
            var expected = new LlmTrainingReport(List.of(), "Хороший результат");
            when(llm.call(eq("training-reviewer"), any(), eq(LlmTrainingReport.class))).thenReturn(expected);

            // when
            var result = llmService.createTrainingReport(request);

            // then
            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
            verify(llm).call(eq("training-reviewer"), requestCaptor.capture(), eq(LlmTrainingReport.class));
            assertThat(requestCaptor.getValue()).isEqualTo(Map.of("JSON_STRING", request));
        }
    }

    @Nested
    @DisplayName("CreateReferenceAnswer")
    class CreateReferenceAnswer {

        @Test
        @DisplayName("Вызывает агента training-reference-answer без грейда, с запросом как есть")
        void callsTrainingReferenceAnswerAgent() {
            // given
            var request = new LlmTrainingReferenceAnswerRequest("Spring Boot", "Java-разработчик", "Что такое JVM?");
            var expected = new LlmTrainingReferenceAnswer("JVM - виртуальная машина Java, которая выполняет байткод");
            when(llm.call(eq("training-reference-answer"), eq(request), eq(LlmTrainingReferenceAnswer.class)))
                    .thenReturn(expected);

            // when
            var result = llmService.createReferenceAnswer(request);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("PlanInterview")
    class PlanInterview {

        @Test
        @DisplayName("Делегирует вызов interviewer.plan с той же вакансией и возвращает результат как есть")
        void delegatesToInterviewer() {
            // given
            var vacancy = new LlmInterviewVacancy(
                    "Java-разработчик", "ООО Ромашка", "От 1 года до 3 лет", List.of("Java"), "Описание");
            var expected = new LlmInterviewPlan(8, List.of("Java core", "Spring"), "Java core", "Что такое JVM?");
            when(interviewer.plan(vacancy)).thenReturn(expected);

            // when
            var result = llmService.planInterview(vacancy);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewer).plan(vacancy);
            verifyNoInteractions(llm);
        }
    }

    @Nested
    @DisplayName("NextInterviewStep")
    class NextInterviewStep {

        @Test
        @DisplayName("Делегирует вызов interviewer.next с теми же аргументами и возвращает результат как есть")
        void delegatesToInterviewer() {
            // given
            var vacancy = new LlmInterviewVacancy(
                    "Java-разработчик", "ООО Ромашка", "От 1 года до 3 лет", List.of("Java"), "Описание");
            var plan = new LlmInterviewPlan(8, List.of("Java core", "Spring"), "Java core", "Что такое JVM?");
            var history = List.of(new LlmInterviewTurn(
                    "Виртуальная машина Java",
                    new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Java core", "Что такое JVM?")));
            var lastAnswer = "Компилирует байткод в машинный код";
            var expected = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "Java core", "А что такое JIT?");
            when(interviewer.next(vacancy, plan, history, lastAnswer)).thenReturn(expected);

            // when
            var result = llmService.nextInterviewStep(vacancy, plan, history, lastAnswer);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewer).next(vacancy, plan, history, lastAnswer);
            verifyNoInteractions(llm);
        }
    }

    @Nested
    @DisplayName("CreateInterviewReport")
    class CreateInterviewReport {

        @ParameterizedTest(name = "опыт \"{0}\" -> суффикс {1}")
        @MethodSource("ru.workbit.llm.service.LlmServiceTest#experienceGrades")
        @DisplayName("Роутит вызов на агента interview-reviewer-{суффикс} по грейду опыта и шлёт запрос одной переменной JSON_STRING")
        void routesByExperienceGradeAndWrapsRequestAsJsonString(String experience, String expectedSuffix) {
            // given
            var request = new LlmInterviewReportRequest("Java-разработчик", experience, List.of());
            var expected = new LlmInterviewReport(List.of(), "HIGH", "Хорошо", "Подтянуть алгоритмы", null);
            when(llm.call(anyString(), any(), eq(LlmInterviewReport.class))).thenReturn(expected);

            // when
            var result = llmService.createInterviewReport(experience, request);

            // then
            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<String> agentKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
            verify(llm).call(agentKeyCaptor.capture(), requestCaptor.capture(), eq(LlmInterviewReport.class));
            assertThat(agentKeyCaptor.getValue()).isEqualTo("interview-reviewer-" + expectedSuffix);
            assertThat(requestCaptor.getValue()).isEqualTo(Map.of("JSON_STRING", request));
        }
    }

    @Nested
    @DisplayName("NormalizeInput")
    class NormalizeInput {

        @Test
        @DisplayName("Вызывает агента input-normalizer без грейда, с запросом как есть")
        void callsInputNormalizerAgent() {
            // given
            var request = new LlmInputNormalizationRequest("многопоточность", "джавист", List.of(), List.of());
            var expected = new LlmInputNormalization(true, List.of(), false, List.of("Java-разработчик"), true);
            when(llm.call(eq("input-normalizer"), eq(request), eq(LlmInputNormalization.class)))
                    .thenReturn(expected);

            // when
            var result = llmService.normalizeInput(request);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }
}
