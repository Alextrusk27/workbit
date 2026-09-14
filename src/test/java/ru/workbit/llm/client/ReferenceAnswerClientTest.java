package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferenceAnswerClientTest")
class ReferenceAnswerClientTest {

    private static final String PROMPT = "Промпт автора эталонных ответов";

    @Mock
    ClaudeClient claude;

    @Captor
    ArgumentCaptor<String> taskCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReferenceAnswerClient referenceAnswerClient;

    @BeforeEach
    void setUp() {
        referenceAnswerClient = new ReferenceAnswerClient(claude, objectMapper,
                new ByteArrayResource(PROMPT.getBytes(StandardCharsets.UTF_8)));
    }

    private static LlmTrainingReferenceAnswerRequest aRequest() {
        return new LlmTrainingReferenceAnswerRequest("SQL", "Аналитик данных",
                "Чем LEFT JOIN отличается от INNER JOIN?");
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("Возвращает ответ модели и шлёт промпт первым аргументом")
        void returnsResponseAndSendsPrompt() {
            // given
            var expected = new LlmTrainingReferenceAnswer("INNER JOIN оставляет только совпавшие пары строк");
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingReferenceAnswer.class)))
                    .thenReturn(expected);

            // when
            var result = referenceAnswerClient.create(aRequest());

            // then
            assertThat(result).isEqualTo(expected);
            verify(claude).ask(eq(PROMPT), eq(taskCaptor.getValue()), eq(LlmTrainingReferenceAnswer.class));
        }

        @Test
        @DisplayName("Вводная - JSON запроса со всеми полями и закрывающая строка-страж")
        void buildsTaskFromRequestJson() {
            // given
            var request = aRequest();
            when(claude.ask(eq(PROMPT), taskCaptor.capture(), eq(LlmTrainingReferenceAnswer.class)))
                    .thenReturn(new LlmTrainingReferenceAnswer("ответ"));

            // when
            referenceAnswerClient.create(request);

            // then
            String task = taskCaptor.getValue();
            assertThat(task).isEqualTo(objectMapper.writeValueAsString(request) + "\nНапиши эталонный ответ.");
            assertThat(task).contains("\"skill\":\"SQL\"", "\"profession\":\"Аналитик данных\"",
                    "\"question\":\"Чем LEFT JOIN отличается от INNER JOIN?\"");
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
            assertThatThrownBy(() -> new ReferenceAnswerClient(claude, objectMapper, missing))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Reference answer prompt is not readable");
        }
    }
}
