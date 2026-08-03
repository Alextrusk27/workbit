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
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
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

    private static final String REFERENCE_ANSWER_AGENT = "training-reference-answer";
    private static final String REFERENCE_ANSWER_PROMPT_ID = "prompt-training-reference-answer";
    private static final String NORMALIZER_AGENT = "input-normalizer";
    private static final String NORMALIZER_PROMPT_ID = "prompt-input-normalizer";

    @Mock
    OpenAIClient client;
    @Mock
    ResponseService responseService;

    private final YandexAiProperties props = new YandexAiProperties(
            "folder-id",
            "api-key",
            Map.of(REFERENCE_ANSWER_AGENT, REFERENCE_ANSWER_PROMPT_ID, NORMALIZER_AGENT, NORMALIZER_PROMPT_ID),
            LogLevel.OFF);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        llmClient = new LlmClient(client, props, objectMapper);
        when(client.responses()).thenReturn(responseService);
    }

    private LlmTrainingReferenceAnswerRequest referenceAnswerRequest() {
        return new LlmTrainingReferenceAnswerRequest(
                "Транзакции", "Java-разработчик", "Расскажите про изоляцию транзакций");
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
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            var response = structuredResponseOf(expected);
            doReturn(response).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(REFERENCE_ANSWER_AGENT, referenceAnswerRequest(), LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result).isEqualTo(expected);
            verify(responseService, times(1)).create(anyParams());
        }

        @Test
        @DisplayName("Делает один повтор и возвращает его, если первый ответ содержит CJK")
        void retriesOnceAndReturnsRetry_whenFirstResponseHasCjk() {
            // given
            var dirty = new LlmTrainingReferenceAnswer("нужен 索引 сейчас");
            var clean = new LlmTrainingReferenceAnswer("нужен индекс сейчас");
            var dirtyResponse = structuredResponseOf(dirty);
            var cleanResponse = structuredResponseOf(clean);
            doReturn(dirtyResponse, cleanResponse).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(REFERENCE_ANSWER_AGENT, referenceAnswerRequest(), LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result).isEqualTo(clean);
            verify(responseService, times(2)).create(anyParams());
        }

        @Test
        @DisplayName("Вырезает CJK и схлопывает двойные пробелы, если CJK остаётся после повтора")
        void stripsCjkAndCollapsesSpaces_whenRetryStillHasCjk() {
            // given
            var dirty = new LlmTrainingReferenceAnswer("используйте 索引 для поиска");
            var firstResponse = structuredResponseOf(dirty);
            var secondResponse = structuredResponseOf(dirty);
            doReturn(firstResponse, secondResponse).when(responseService).create(anyParams());

            // when
            var result = llmClient.call(REFERENCE_ANSWER_AGENT, referenceAnswerRequest(), LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result.answer()).isEqualTo("используйте для поиска");
            verify(responseService, times(2)).create(anyParams());
        }

        @Test
        @DisplayName("Обнаруживает CJK в любом строковом поле DTO, включая элементы вложенного списка")
        void detectsCjk_inAnyStringField() {
            // given
            var request = new LlmInputNormalizationRequest("транзакции", "java-разработчик");
            var dirty = new LlmInputNormalization(
                    false, List.of("транзакции基础", "изоляция"),
                    true, List.of("бэкенд-разработчик"),
                    true);
            var clean = new LlmInputNormalization(
                    false, List.of("транзакции", "изоляция"),
                    true, List.of("бэкенд-разработчик"),
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
            assertThatThrownBy(() -> llmClient.call(REFERENCE_ANSWER_AGENT, referenceAnswerRequest(), LlmTrainingReferenceAnswer.class))
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
            assertThatThrownBy(() -> llmClient.call(REFERENCE_ANSWER_AGENT, referenceAnswerRequest(), LlmTrainingReferenceAnswer.class))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(openAiException);
            verify(responseService, times(1)).create(anyParams());
        }
    }
}
