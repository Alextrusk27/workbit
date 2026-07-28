package ru.workbit.llm.service;

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
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.dto.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmServiceTest")
class LlmServiceTest {

    @Mock
    LlmClient llm;

    @InjectMocks
    LlmService llmService;

    private static Stream<Arguments> experienceGrades() {
        return Stream.of(
                Arguments.of("Нет опыта", "noexp"),
                Arguments.of("От 3 до 6 лет", "middle"),
                Arguments.of("Более 6 лет", "senior"),
                Arguments.of("От 1 года до 3 лет", "junior"),
                Arguments.of("", "junior"),
                Arguments.of(null, "junior"),
                Arguments.of("Неизвестная категория опыта", "junior")
        );
    }

    @Nested
    @DisplayName("GenerateTrainingQuestions")
    class GenerateTrainingQuestions {

        @Test
        @DisplayName("Вызывает агента training-question-generator без грейда, с запросом как есть")
        void callsTrainingQuestionGeneratorAgent() {
            // given
            var request = new LlmTrainingQuestionsRequest("Java-разработчик", null, "middle", 5, List.of());
            var expected = new LlmTrainingQuestions(List.of("Что такое JVM?"));
            when(llm.call(eq("training-question-generator"), eq(request), eq(LlmTrainingQuestions.class)))
                    .thenReturn(expected);

            // when
            var result = llmService.generateTrainingQuestions(request);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("DecideTrainingFollowUp")
    class DecideTrainingFollowUp {

        @Test
        @DisplayName("Вызывает агента training-follow-up без грейда, с запросом как есть")
        void callsTrainingFollowUpAgent() {
            // given
            var request = new LlmTrainingFollowUpRequest(
                    "Java-разработчик", "middle", "Что такое JVM?", "Виртуальная машина", List.of());
            var expected = new LlmTrainingFollowUpDecision(true, "А что такое сборка мусора?");
            when(llm.call(eq("training-follow-up"), eq(request), eq(LlmTrainingFollowUpDecision.class)))
                    .thenReturn(expected);

            // when
            var result = llmService.decideTrainingFollowUp(request);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("CreateTrainingReport")
    class CreateTrainingReport {

        @Test
        @DisplayName("Вызывает агента training-reviewer с запросом одной переменной JSON_STRING, а не полями DTO")
        void callsTrainingReviewerAgentWithJsonStringVariable() {
            // given
            var request = new LlmTrainingReportRequest("Java-разработчик", "middle", List.of());
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
    @DisplayName("GenerateInterviewQuestions")
    class GenerateInterviewQuestions {

        @ParameterizedTest(name = "опыт \"{0}\" -> суффикс {1}")
        @MethodSource("ru.workbit.llm.service.LlmServiceTest#experienceGrades")
        @DisplayName("Роутит вызов на агента interview-question-generator-{суффикс} по грейду опыта")
        void routesByExperienceGrade(String experience, String expectedSuffix) {
            // given
            var request = new LlmInterviewQuestionsRequest(
                    "Java-разработчик", "ООО Ромашка", List.of("Java"), "Описание", 5, 10);
            var expected = new LlmInterviewQuestions(List.of("Что такое JVM?"));
            when(llm.call(anyString(), eq(request), eq(LlmInterviewQuestions.class))).thenReturn(expected);

            // when
            var result = llmService.generateInterviewQuestions(experience, request);

            // then
            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<String> agentKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(llm).call(agentKeyCaptor.capture(), eq(request), eq(LlmInterviewQuestions.class));
            assertThat(agentKeyCaptor.getValue()).isEqualTo("interview-question-generator-" + expectedSuffix);
        }
    }

    @Nested
    @DisplayName("DecideInterviewFollowUp")
    class DecideInterviewFollowUp {

        @ParameterizedTest(name = "опыт \"{0}\" -> суффикс {1}")
        @MethodSource("ru.workbit.llm.service.LlmServiceTest#experienceGrades")
        @DisplayName("Роутит вызов на агента interview-follow-up-{суффикс} по грейду опыта")
        void routesByExperienceGrade(String experience, String expectedSuffix) {
            // given
            var request = new LlmInterviewFollowUpRequest(
                    "Java-разработчик", "Что такое JVM?", "Виртуальная машина", List.of());
            var expected = new LlmInterviewFollowUpDecision(false, null);
            when(llm.call(anyString(), eq(request), eq(LlmInterviewFollowUpDecision.class))).thenReturn(expected);

            // when
            var result = llmService.decideInterviewFollowUp(experience, request);

            // then
            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<String> agentKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(llm).call(agentKeyCaptor.capture(), eq(request), eq(LlmInterviewFollowUpDecision.class));
            assertThat(agentKeyCaptor.getValue()).isEqualTo("interview-follow-up-" + expectedSuffix);
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
            var expected = new LlmInterviewReport(List.of(), "HIGH", "Хорошо", "Подтянуть алгоритмы");
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
            var request = new LlmInputNormalizationRequest("джавист", "многопоточность");
            var expected = new LlmInputNormalization(false, List.of("Java-разработчик"), true, List.of(), true);
            when(llm.call(eq("input-normalizer"), eq(request), eq(LlmInputNormalization.class)))
                    .thenReturn(expected);

            // when
            var result = llmService.normalizeInput(request);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }
}
