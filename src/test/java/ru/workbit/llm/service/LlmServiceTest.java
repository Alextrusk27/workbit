package ru.workbit.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.llm.client.InterviewerClient;
import ru.workbit.llm.client.LlmClient;
import ru.workbit.llm.client.NormalizerClient;
import ru.workbit.llm.client.QuestionGeneratorClient;
import ru.workbit.llm.client.ReferenceAnswerClient;
import ru.workbit.llm.client.ReviewerClient;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmInterviewAnswer;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTopicKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import ru.workbit.llm.dto.LlmOfferProbability;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import ru.workbit.training.model.TrainingSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmServiceTest")
class LlmServiceTest {

    private static final String ASKED_BEFORE = "Уже задавалось:\n- Что такое JVM?";

    @Mock
    LlmClient llm;

    @Mock
    InterviewerClient interviewer;

    @Mock
    ReviewerClient reviewer;

    @Mock
    NormalizerClient normalizer;

    @Mock
    QuestionGeneratorClient questionGenerator;

    @Mock
    ReferenceAnswerClient referenceAnswer;

    @InjectMocks
    LlmService llmService;

    @Nested
    @DisplayName("GenerateTrainingQuestions")
    class GenerateTrainingQuestions {

        @ParameterizedTest(name = "уровень {0}")
        @EnumSource(TrainingSession.Level.class)
        @DisplayName("Делегирует составителю вопросов запрос как есть, с грейдом уровня в поле level")
        void delegatesToQuestionGenerator(TrainingSession.Level level) {
            // given
            var request = new LlmTrainingQuestionsRequest(
                    "Spring Boot", "Java-разработчик", level.getGrade(), 5, List.of());
            var expected = new LlmTrainingQuestions(List.of("Что такое JVM?"));
            when(questionGenerator.generate(request)).thenReturn(expected);

            // when
            var result = llmService.generateTrainingQuestions(request);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llm);
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
        @DisplayName("Делегирует автору эталонов запрос как есть, без грейда")
        void delegatesToReferenceAnswerClient() {
            // given
            var request = new LlmTrainingReferenceAnswerRequest("Spring Boot", "Java-разработчик", "Что такое JVM?");
            var expected = new LlmTrainingReferenceAnswer("JVM - виртуальная машина Java, которая выполняет байткод");
            when(referenceAnswer.create(request)).thenReturn(expected);

            // when
            var result = llmService.createReferenceAnswer(request);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llm);
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
            var expected = new LlmInterviewPlan(8,
                    List.of(new LlmInterviewTopic("Java core", 5, LlmInterviewTopicKind.CORE),
                            new LlmInterviewTopic("Spring", 3, LlmInterviewTopicKind.STANDARD)),
                    "Java core", "Что такое JVM?");
            when(interviewer.plan(vacancy, ASKED_BEFORE)).thenReturn(expected);

            // when
            var result = llmService.planInterview(vacancy, ASKED_BEFORE);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewer).plan(vacancy, ASKED_BEFORE);
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
            var plan = new LlmInterviewPlan(8,
                    List.of(new LlmInterviewTopic("Java core", 5, LlmInterviewTopicKind.CORE),
                            new LlmInterviewTopic("Spring", 3, LlmInterviewTopicKind.STANDARD)),
                    "Java core", "Что такое JVM?");
            var history = List.of(new LlmInterviewTurn(
                    "Виртуальная машина Java",
                    new LlmInterviewStep(LlmInterviewStepKind.MAIN, "Java core", "Что такое JVM?")));
            var lastAnswer = "Компилирует байткод в машинный код";
            var expected = new LlmInterviewStep(LlmInterviewStepKind.FOLLOW_UP, "Java core", "А что такое JIT?");
            when(interviewer.next(vacancy, plan, history, lastAnswer, ASKED_BEFORE)).thenReturn(expected);

            // when
            var result = llmService.nextInterviewStep(vacancy, plan, history, lastAnswer, ASKED_BEFORE);

            // then
            assertThat(result).isEqualTo(expected);
            verify(interviewer).next(vacancy, plan, history, lastAnswer, ASKED_BEFORE);
            verifyNoInteractions(llm);
        }
    }

    @Nested
    @DisplayName("CreateInterviewReport")
    class CreateInterviewReport {

        @Test
        @DisplayName("Делегирует вызов reviewer.review с теми же аргументами и возвращает результат как есть")
        void delegatesToReviewer() {
            // given
            var vacancy = new LlmInterviewVacancy(
                    "Java-разработчик", "ООО Ромашка", "От 1 года до 3 лет", List.of("Java"), "Описание");
            var answers = List.of(new LlmInterviewAnswer(
                    1, "Java core", "Что такое JVM?", "Виртуальная машина Java", List.of()));
            var expected = new LlmInterviewReport(
                    List.of(), LlmOfferProbability.HIGH, "Хорошо", "Подтянуть алгоритмы", null);
            when(reviewer.review(vacancy, answers)).thenReturn(expected);

            // when
            var result = llmService.createInterviewReport(vacancy, answers);

            // then
            assertThat(result).isEqualTo(expected);
            verify(reviewer).review(vacancy, answers);
            verifyNoInteractions(llm);
        }
    }

    @Nested
    @DisplayName("NormalizeInput")
    class NormalizeInput {

        @Test
        @DisplayName("Делегирует нормализатору запрос как есть")
        void delegatesToNormalizer() {
            // given
            var request = new LlmInputNormalizationRequest("многопоточность", "джавист", List.of(), List.of());
            var expected = new LlmInputNormalization(true, List.of(), false, List.of("Java-разработчик"), true);
            when(normalizer.normalize(request)).thenReturn(expected);

            // when
            var result = llmService.normalizeInput(request);

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(llm);
        }
    }
}
