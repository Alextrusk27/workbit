package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionMessage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.OpenAiProperties;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("NormalizerClientTest")
class NormalizerClientTest {

    @Mock
    OpenAIClient client;
    @Mock
    ChatService chatService;
    @Mock
    ChatCompletionService completionService;

    private final OpenAiProperties props = new OpenAiProperties("gpt-5.4-mini");

    private NormalizerClient normalizerClient;

    @BeforeEach
    void setUp() {
        normalizerClient = new NormalizerClient(client, props, new ObjectMapper(),
                new ByteArrayResource("prompt".getBytes()));
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
    }

    private static LlmInputNormalizationRequest request() {
        return new LlmInputNormalizationRequest("спринг бут", "джава дев", List.of(), List.of());
    }

    private static <T> StructuredChatCompletionCreateParams<T> anyParams() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private static <T> StructuredChatCompletion<T> completionOf(T dto) {
        StructuredChatCompletionMessage<T> message = mock(StructuredChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.of(dto));

        StructuredChatCompletion.Choice<T> choice = mock(StructuredChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);

        StructuredChatCompletion<T> completion = mock(StructuredChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));

        return completion;
    }

    @Nested
    @DisplayName("Normalize")
    class Normalize {

        @Test
        @DisplayName("Возвращает разобранный ответ модели")
        void returnsParsedResponse() {
            // given
            var expected = new LlmInputNormalization(
                    true, List.of("Spring Boot", "Spring Framework"),
                    true, List.of("Java-разработчик", "Backend-разработчик на Java"),
                    true);
            doReturn(completionOf(expected)).when(completionService).create(anyParams());

            // when
            var result = normalizerClient.normalize(request());

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Бросает LlmException, если в ответе нет разобранного содержимого")
        void throwsLlmException_whenNoContent() {
            // given
            StructuredChatCompletion<LlmInputNormalization> completion = emptyCompletion();
            doReturn(completion).when(completionService).create(anyParams());

            // when / then
            assertThatThrownBy(() -> normalizerClient.normalize(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Model not response");
        }

        @Test
        @DisplayName("Оборачивает OpenAIServiceException в LlmException с кодом статуса")
        void wrapsServiceException_intoLlmException() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .build();
            doThrow(serviceException).when(completionService).create(anyParams());

            // when / then
            assertThatThrownBy(() -> normalizerClient.normalize(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed with status 400")
                    .hasCause(serviceException);
        }

        @Test
        @DisplayName("Оборачивает прочие ошибки клиента в LlmException")
        void wrapsGenericException_intoLlmException() {
            // given
            var openAiException = new OpenAIException("network timeout");
            doThrow(openAiException).when(completionService).create(anyParams());

            // when / then
            assertThatThrownBy(() -> normalizerClient.normalize(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(openAiException);
        }

        @SuppressWarnings("unchecked")
        private StructuredChatCompletion<LlmInputNormalization> emptyCompletion() {
            StructuredChatCompletion<LlmInputNormalization> completion = mock(StructuredChatCompletion.class);
            when(completion.choices()).thenReturn(List.of());
            return completion;
        }
    }
}
