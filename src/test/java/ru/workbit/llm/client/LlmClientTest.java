package ru.workbit.llm.client;

import com.openai.client.OpenAIClient;
import com.openai.core.LogLevel;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import com.openai.models.responses.StructuredResponseOutputMessage;
import com.openai.services.blocking.ResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.YandexAiProperties;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import ru.workbit.llm.dto.LlmTrainingFollowUp;
import ru.workbit.llm.dto.LlmTrainingFollowUpDecision;
import ru.workbit.llm.dto.LlmTrainingFollowUpRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmClientTest")
class LlmClientTest {

    private static final String FOLLOW_UP_AGENT = "training-follow-up";
    private static final String FOLLOW_UP_PROMPT_ID = "prompt-training-follow-up";
    private static final String NORMALIZER_AGENT = "input-normalizer";
    private static final String NORMALIZER_PROMPT_ID = "prompt-input-normalizer";

    @Mock
    OpenAIClient client;
    @Mock
    ResponseService responseService;

    private final YandexAiProperties props = new YandexAiProperties(
            "folder-id",
            "api-key",
            Map.of(FOLLOW_UP_AGENT, FOLLOW_UP_PROMPT_ID, NORMALIZER_AGENT, NORMALIZER_PROMPT_ID),
            LogLevel.OFF);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new LlmClient(client, props, objectMapper);
        when(client.responses()).thenReturn(responseService);
    }

    private LlmTrainingFollowUpRequest followUpRequest() {
        return new LlmTrainingFollowUpRequest(
                "Java-разработчик",
                "middle",
                "Расскажите про изоляцию транзакций",
                "Используется уровень READ COMMITTED",
                List.of(new LlmTrainingFollowUp("Что такое дедлок?", "Взаимная блокировка ресурсов")));
    }

    private static <T> StructuredResponseCreateParams<T> anyParams() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private static <T> StructuredResponse<T> structuredResponseOf(T dto) {
        StructuredResponseOutputMessage.Content<T> content = mock(StructuredResponseOutputMessage.Content.class);
        when(content.outputText()).thenReturn(Optional.of(dto));

        StructuredResponseOutputMessage<T> message = mock(StructuredResponseOutputMessage.class);
        when(message.content()).thenReturn(List.of(content));

        StructuredResponseOutputItem<T> item = mock(StructuredResponseOutputItem.class);
        when(item.message()).thenReturn(Optional.of(message));

        StructuredResponse<T> response = mock(StructuredResponse.class);
        when(response.output()).thenReturn(List.of(item));

        return response;
    }

    @Nested
    @DisplayName("Call")
    class Call {

        @Test
        @DisplayName("Возвращает ответ как есть, если в нём нет CJK-символов")
        void returnsResponseAsIs_whenNoCjk() {
            // given
            var expected = new LlmTrainingFollowUpDecision(true, "используйте индекс для поиска");
            var response = structuredResponseOf(expected);
            doReturn(response).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(FOLLOW_UP_AGENT, followUpRequest(), LlmTrainingFollowUpDecision.class);

            // then
            assertThat(result).isEqualTo(expected);
            verify(responseService, times(1)).create(anyParams());
        }

        @Test
        @DisplayName("Делает один повтор и возвращает его, если первый ответ содержит CJK")
        void retriesOnceAndReturnsRetry_whenFirstResponseHasCjk() {
            // given
            var dirty = new LlmTrainingFollowUpDecision(true, "нужен 索引 сейчас");
            var clean = new LlmTrainingFollowUpDecision(true, "нужен индекс сейчас");
            var dirtyResponse = structuredResponseOf(dirty);
            var cleanResponse = structuredResponseOf(clean);
            doReturn(dirtyResponse, cleanResponse).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(FOLLOW_UP_AGENT, followUpRequest(), LlmTrainingFollowUpDecision.class);

            // then
            assertThat(result).isEqualTo(clean);
            verify(responseService, times(2)).create(anyParams());
        }

        @Test
        @DisplayName("Вырезает CJK и схлопывает двойные пробелы, если CJK остаётся после повтора")
        void stripsCjkAndCollapsesSpaces_whenRetryStillHasCjk() {
            // given
            var dirty = new LlmTrainingFollowUpDecision(true, "используйте 索引 для поиска");
            var firstResponse = structuredResponseOf(dirty);
            var secondResponse = structuredResponseOf(dirty);
            doReturn(firstResponse, secondResponse).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(FOLLOW_UP_AGENT, followUpRequest(), LlmTrainingFollowUpDecision.class);

            // then
            assertThat(result.askFollowUp()).isTrue();
            assertThat(result.question()).isEqualTo("используйте для поиска");
            verify(responseService, times(2)).create(anyParams());
        }

        @Test
        @DisplayName("Обнаруживает CJK в любом строковом поле DTO, включая элементы вложенного списка")
        void detectsCjk_inAnyStringField() {
            // given
            var request = new LlmInputNormalizationRequest("java-разработчик", "транзакции");
            var dirty = new LlmInputNormalization(
                    true, List.of("бэкенд-разработчик"),
                    false, List.of("транзакции基础", "изоляция"),
                    true);
            var clean = new LlmInputNormalization(
                    true, List.of("бэкенд-разработчик"),
                    false, List.of("транзакции", "изоляция"),
                    true);
            var dirtyResponse = structuredResponseOf(dirty);
            var cleanResponse = structuredResponseOf(clean);
            doReturn(dirtyResponse, cleanResponse).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(NORMALIZER_AGENT, request, LlmInputNormalization.class);

            // then
            assertThat(result).isEqualTo(clean);
            verify(responseService, times(2)).create(anyParams());
        }

        @Test
        @DisplayName("Оборачивает OpenAIServiceException в LlmException с кодом статуса")
        void wrapsServiceException_intoLlmException() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .build();
            doThrow(serviceException).when(responseService).create(anyParams());

            // when / then
            assertThatThrownBy(() -> llmClient.call(FOLLOW_UP_AGENT, followUpRequest(), LlmTrainingFollowUpDecision.class))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed with status 400")
                    .hasCause(serviceException);
            verify(responseService, times(1)).create(anyParams());
        }

        @Test
        @DisplayName("Оборачивает произвольный OpenAIException в LlmException")
        void wrapsGenericOpenAiException_intoLlmException() {
            // given
            var openAiException = new OpenAIException("network timeout");
            doThrow(openAiException).when(responseService).create(anyParams());

            // when / then
            assertThatThrownBy(() -> llmClient.call(FOLLOW_UP_AGENT, followUpRequest(), LlmTrainingFollowUpDecision.class))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(openAiException);
            verify(responseService, times(1)).create(anyParams());
        }
    }
}
