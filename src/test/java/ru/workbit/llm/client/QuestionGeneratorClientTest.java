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
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionGeneratorClientTest")
class QuestionGeneratorClientTest {

    private static final String PROMPT = "Промпт составителя вопросов";

    @Mock
    ClaudeClient claude;

    @Captor
    ArgumentCaptor<String> taskCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QuestionGeneratorClient questionGeneratorClient;

    @BeforeEach
    void setUp() {
        questionGeneratorClient = new QuestionGeneratorClient(claude, objectMapper,
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)));
    }

    private static LlmTrainingQuestionsRequest aRequest() {
        return new LlmTrainingQuestionsRequest("SQL", "Аналитик данных", "junior", 3,
                List.of("Что такое индекс?"));
    }

    @Nested
    @DisplayName("Generate")
    class Generate {

        @Test
        @DisplayName("Возвращает ответ модели и шлёт промпт первым аргументом")
        void returnsResponseAndSendsPrompt() {
            // given
            var expected = new LlmTrainingQuestions(List.of("Чем LEFT JOIN отличается от INNER JOIN?"));
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingQuestions.class)))
                    .thenReturn(expected);

            // when
            var result = questionGeneratorClient.generate(aRequest());

            // then
            assertThat(result).isEqualTo(expected);
            verify(claude).ask(eq(PROMPT), eq(taskCaptor.getValue()), eq(LlmTrainingQuestions.class));
        }

        @Test
        @DisplayName("Вводная - JSON запроса со всеми полями и закрывающая строка-страж")
        void buildsTaskFromRequestJson() {
            // given
            var request = aRequest();
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingQuestions.class)))
                    .thenReturn(new LlmTrainingQuestions(List.of()));

            // when
            questionGeneratorClient.generate(request);

            // then
            String task = taskCaptor.getValue();
            assertThat(task).isEqualTo(objectMapper.writeValueAsString(request) + "\nСоставь вопросы.");
            assertThat(task).contains("\"skill\":\"SQL\"", "\"profession\":\"Аналитик данных\"",
                    "\"level\":\"junior\"", "\"count\":3", "\"existingQuestions\":[\"Что такое индекс?\"]");
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
            assertThatThrownBy(() -> new QuestionGeneratorClient(claude, objectMapper, missing))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Question generator prompt is not readable");
        }
    }
}
