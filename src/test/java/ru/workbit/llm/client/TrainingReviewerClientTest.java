package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import ru.workbit.llm.dto.LlmTrainingCase;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrainingReviewerClientTest")
class TrainingReviewerClientTest {

    private static final String PROMPT = "Промпт рецензента тренажёра";

    @Mock
    ClaudeClient claude;

    @Captor
    ArgumentCaptor<String> taskCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TrainingReviewerClient trainingReviewerClient;

    @BeforeEach
    void setUp() {
        trainingReviewerClient = new TrainingReviewerClient(claude, objectMapper,
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)));
    }

    private static LlmTrainingReportRequest aRequest() {
        return new LlmTrainingReportRequest("SQL", "Аналитик данных",
                List.of(new LlmTrainingCase(1, "Чем LEFT JOIN отличается от INNER JOIN?",
                        "left join берёт все строки из левой таблицы")));
    }

    @Nested
    @DisplayName("Review")
    class Review {

        @Test
        @DisplayName("Возвращает отчёт модели и шлёт промпт первым аргументом")
        void returnsResponseAndSendsPrompt() {
            // given
            var expected = new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "В ответе раскрыта только одна сторона сравнения", 3)),
                    "Стоит разобрать, когда LEFT JOIN даёт лишние строки");
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingReport.class))).thenReturn(expected);

            // when
            var result = trainingReviewerClient.review(aRequest());

            // then
            assertThat(result).isEqualTo(expected);
            verify(claude).ask(eq(PROMPT), eq(taskCaptor.getValue()), eq(LlmTrainingReport.class));
        }

        @Test
        @DisplayName("Вводная - JSON запроса со всеми кейсами и закрывающая строка-страж")
        void buildsTaskFromRequestJson() {
            // given
            var request = aRequest();
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingReport.class)))
                    .thenReturn(new LlmTrainingReport(List.of(), "итог"));

            // when
            trainingReviewerClient.review(request);

            // then
            String task = taskCaptor.getValue();
            assertThat(task).isEqualTo(objectMapper.writeValueAsString(request) + "\nСоставь отчёт.");
            assertThat(task).contains("\"skill\":\"SQL\"", "\"profession\":\"Аналитик данных\"",
                    "\"index\":1", "\"question\":\"Чем LEFT JOIN отличается от INNER JOIN?\"");
        }
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Нечитаемый промпт - LlmException при создании бина")
        void throwsLlmException_whenPromptUnreadable() {
            // given
            var missing = new ClassPathResource("llm/no-such-prompt.txt");

            // when / then
            assertThatThrownBy(() -> new TrainingReviewerClient(claude, objectMapper, missing))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Training reviewer prompt is not readable");
        }
    }
}
