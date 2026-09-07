package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.models.messages.MessageParam;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReply;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTopicKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewerClientTest")
class InterviewerClientTest {

    private static final String PROMPT = "Промпт интервьюера";
    private static final String ASKED_BEFORE = "Уже задавалось:\n- Что такое интерфейс?";

    @Mock
    ClaudeClient claude;

    @Captor
    ArgumentCaptor<List<MessageParam>> dialogCaptor;

    @Captor
    ArgumentCaptor<List<String>> openingCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InterviewerClient interviewerClient;

    @BeforeEach
    void setUp() {
        interviewerClient = new InterviewerClient(claude, objectMapper,
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)));
    }

    private static LlmInterviewVacancy aVacancy() {
        return new LlmInterviewVacancy("Java-разработчик", "ООО Ромашка", "От 1 года до 3 лет",
                List.of("Java", "SQL"), "Описание вакансии");
    }

    private static LlmInterviewPlan aPlan(int questionCount, List<LlmInterviewTopic> topics) {
        return new LlmInterviewPlan(questionCount, topics, "SOLID", "Расскажите про SOLID");
    }

    private static List<LlmInterviewTopic> planTopics() {
        return List.of(new LlmInterviewTopic("SOLID", 3, LlmInterviewTopicKind.CORE),
                new LlmInterviewTopic("SQL", 2, LlmInterviewTopicKind.STANDARD));
    }

    private static LlmInterviewTurn aTurn(String answer, LlmInterviewStepKind kind, String topic, String question) {
        return new LlmInterviewTurn(answer, new LlmInterviewStep(kind, topic, question));
    }

    private static String contentOf(List<MessageParam> dialog, int index) {
        return dialog.get(index).content().asString();
    }

    @Nested
    @DisplayName("Plan")
    class Plan {

        @Test
        @DisplayName("Первый ход: промпт, вводная с вакансией и коридором вопросов, пустой диалог")
        void sendsOpeningWithVacancyAndQuestionRange() {
            // given
            LlmInterviewVacancy vacancy = aVacancy();
            LlmInterviewReply reply = new LlmInterviewReply(LlmInterviewStepKind.MAIN, 7, planTopics(),
                    "SOLID", "Расскажите про SOLID");
            when(claude.converse(any(), any(), any(), any(), eq(LlmInterviewReply.class))).thenReturn(reply);

            // when
            LlmInterviewPlan plan = interviewerClient.plan(vacancy, null);

            // then
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> lastUserCaptor = ArgumentCaptor.forClass(String.class);
            verify(claude).converse(promptCaptor.capture(), openingCaptor.capture(), dialogCaptor.capture(),
                    lastUserCaptor.capture(), eq(LlmInterviewReply.class));

            assertThat(promptCaptor.getValue()).isEqualTo(PROMPT);
            assertThat(openingCaptor.getValue()).singleElement(STRING)
                    .contains(objectMapper.writeValueAsString(vacancy))
                    .contains("Вопросов: от %d до %d.".formatted(
                            LlmInterviewPlan.MIN_COUNT, LlmInterviewPlan.MAX_COUNT));
            assertThat(dialogCaptor.getValue()).isEmpty();
            assertThat(lastUserCaptor.getValue()).isNull();

            assertThat(plan).isEqualTo(new LlmInterviewPlan(7, planTopics(), "SOLID", "Расскажите про SOLID"));
        }

        @Test
        @DisplayName("Модель не вернула questionCount - в плане 0, коридор доводит код сервиса")
        void returnsZeroQuestionCountWhenModelSkippedIt() {
            // given
            LlmInterviewReply reply = new LlmInterviewReply(LlmInterviewStepKind.MAIN, null, planTopics(),
                    "SOLID", "Расскажите про SOLID");
            when(claude.converse(any(), any(), any(), any(), eq(LlmInterviewReply.class))).thenReturn(reply);

            // when
            LlmInterviewPlan plan = interviewerClient.plan(aVacancy(), null);

            // then
            assertThat(plan.questionCount()).isZero();
        }

        @Test
        @DisplayName("Вопросы прошлых интервью уходят отдельным блоком вводной, следом за вакансией")
        void sendsAskedBeforeAsSecondOpeningBlock() {
            // given
            LlmInterviewReply reply = new LlmInterviewReply(LlmInterviewStepKind.MAIN, 7, planTopics(),
                    "SOLID", "Расскажите про SOLID");
            when(claude.converse(any(), any(), any(), any(), eq(LlmInterviewReply.class))).thenReturn(reply);

            // when
            interviewerClient.plan(aVacancy(), ASKED_BEFORE);

            // then
            verify(claude).converse(any(), openingCaptor.capture(), any(), any(), eq(LlmInterviewReply.class));
            assertThat(openingCaptor.getValue()).hasSize(2).last(STRING).isEqualTo(ASKED_BEFORE);
        }

        @Test
        @DisplayName("Промпт не читается - LlmException")
        void throwsWhenPromptResourceIsNotReadable() {
            // when / then
            assertThatThrownBy(() -> new InterviewerClient(claude, objectMapper,
                    new ClassPathResource("llm/missing-interviewer.txt")))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Interviewer prompt is not readable");
        }
    }

    @Nested
    @DisplayName("Next")
    class Next {

        private static final String STEP_TOPIC = "SQL";

        private LlmInterviewStep next(LlmInterviewPlan plan, List<LlmInterviewTurn> history, String lastAnswer) {
            return interviewerClient.next(aVacancy(), plan, history, lastAnswer, null);
        }

        private void stubReply() {
            LlmInterviewReply reply = new LlmInterviewReply(LlmInterviewStepKind.MAIN, null, null,
                    STEP_TOPIC, "Расскажите про индексы");
            when(claude.converse(any(), any(), any(), any(), eq(LlmInterviewReply.class))).thenReturn(reply);
        }

        private String captureLastUser() {
            ArgumentCaptor<String> lastUserCaptor = ArgumentCaptor.forClass(String.class);
            verify(claude).converse(any(), any(), dialogCaptor.capture(), lastUserCaptor.capture(),
                    eq(LlmInterviewReply.class));
            return lastUserCaptor.getValue();
        }

        @Test
        @DisplayName("К каждому ответу кандидата дописываются счётчики основных и по теме текущего вопроса")
        void appendsMainAndTopicCountersToEveryCandidateAnswer() {
            // given
            LlmInterviewPlan plan = aPlan(5, planTopics());
            List<LlmInterviewTurn> history = List.of(
                    aTurn("Ответ 1", LlmInterviewStepKind.MAIN, "SQL", "Расскажите про индексы"),
                    aTurn("Ответ 2", LlmInterviewStepKind.FOLLOW_UP, "SQL", "А про покрывающие?"));
            stubReply();

            // when
            LlmInterviewStep step = next(plan, history, "Ответ 3");

            // then
            String lastUser = captureLastUser();
            List<MessageParam> dialog = dialogCaptor.getValue();
            assertThat(dialog).hasSize(5);
            assertThat(contentOf(dialog, 0)).isEqualTo(objectMapper.writeValueAsString(plan));
            assertThat(contentOf(dialog, 1)).isEqualTo(
                    "Ответ кандидата: Ответ 1\nОсновных задано: 1 из 5. По теме «SOLID» задано 1 из 3.");
            assertThat(contentOf(dialog, 3)).isEqualTo(
                    "Ответ кандидата: Ответ 2\nОсновных задано: 2 из 5. По теме «SQL» задано 1 из 2.");
            assertThat(lastUser).isEqualTo(
                    "Ответ кандидата: Ответ 3\nОсновных задано: 2 из 5. По теме «SQL» задано 1 из 2.");
            assertThat(step).isEqualTo(
                    new LlmInterviewStep(LlmInterviewStepKind.MAIN, STEP_TOPIC, "Расскажите про индексы"));
        }

        @Test
        @DisplayName("Реплика хода k приходит ходом k+1 байт в байт - кэш промпта не промахивается")
        void keepsCandidateReplyIdenticalBetweenTurns() {
            // given
            LlmInterviewPlan plan = aPlan(5, planTopics());
            LlmInterviewTurn first = aTurn("Ответ 1", LlmInterviewStepKind.MAIN, "SQL", "Расскажите про индексы");
            LlmInterviewTurn second = aTurn("Ответ 2", LlmInterviewStepKind.FOLLOW_UP, "SQL", "А про покрывающие?");
            stubReply();

            // when
            next(plan, List.of(first), "Ответ 2");
            next(plan, List.of(first, second), "Ответ 3");

            // then
            ArgumentCaptor<String> lastUserCaptor = ArgumentCaptor.forClass(String.class);
            verify(claude, times(2)).converse(any(), any(), dialogCaptor.capture(), lastUserCaptor.capture(),
                    eq(LlmInterviewReply.class));

            List<MessageParam> firstDialog = dialogCaptor.getAllValues().getFirst();
            List<MessageParam> secondDialog = dialogCaptor.getAllValues().getLast();
            assertThat(contentOf(secondDialog, 0)).isEqualTo(contentOf(firstDialog, 0));
            assertThat(contentOf(secondDialog, 1)).isEqualTo(contentOf(firstDialog, 1));
            assertThat(contentOf(secondDialog, 3)).isEqualTo(lastUserCaptor.getAllValues().getFirst());
        }

        @Test
        @DisplayName("Блок с прошлыми вопросами повторяется на каждом ходе - вводная между ходами не меняется")
        void keepsAskedBeforeInOpeningOnEveryTurn() {
            // given
            LlmInterviewPlan plan = aPlan(5, planTopics());
            stubReply();

            // when
            interviewerClient.next(aVacancy(), plan, List.of(), "Ответ 1", ASKED_BEFORE);

            // then
            verify(claude).converse(any(), openingCaptor.capture(), any(), any(), eq(LlmInterviewReply.class));
            assertThat(openingCaptor.getValue()).hasSize(2).last(STRING).isEqualTo(ASKED_BEFORE);
        }

        @Test
        @DisplayName("Основные вопросы исчерпаны - счётчика по теме нет, а сам счётчик предупреждает о конце")
        void skipsTopicCounterWhenMainQuestionsAreExhausted() {
            // given
            LlmInterviewPlan plan = aPlan(1, List.of(new LlmInterviewTopic("SOLID", 1, LlmInterviewTopicKind.CORE)));
            stubReply();

            // when
            next(plan, List.of(), "Ответ 1");

            // then
            assertThat(captureLastUser())
                    .isEqualTo("Ответ кандидата: Ответ 1\nОсновных задано: 1 из 1, новых основных не будет.");
        }

        @Test
        @DisplayName("Легаси-сессия без структурных тем плана - счётчика по теме нет")
        void skipsTopicCounterWhenPlanHasNoTopics() {
            // given
            LlmInterviewPlan plan = aPlan(5, null);
            stubReply();

            // when
            next(plan, List.of(), "Ответ 1");

            // then
            assertThat(captureLastUser()).isEqualTo("Ответ кандидата: Ответ 1\nОсновных задано: 1 из 5.");
        }

        @Test
        @DisplayName("Модель ушла на тему вне плана - счётчика по теме нет")
        void skipsTopicCounterWhenTopicIsOutsideThePlan() {
            // given
            LlmInterviewPlan plan = aPlan(5, planTopics());
            List<LlmInterviewTurn> history = List.of(
                    aTurn("Ответ 1", LlmInterviewStepKind.MAIN, "Kubernetes", "Как разворачиваете сервисы?"));
            stubReply();

            // when
            next(plan, history, "Ответ 2");

            // then
            assertThat(captureLastUser()).isEqualTo("Ответ кандидата: Ответ 2\nОсновных задано: 2 из 5.");
        }
    }
}
