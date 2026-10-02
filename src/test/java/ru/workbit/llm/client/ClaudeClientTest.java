package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import com.openai.core.http.Headers;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.ClaudeProperties;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;

/**
 * Ответ модели стабится потоком настоящих SDK-чанков: текст, чанк с причиной остановки и чанк с
 * usage без choices. Текст ответа разбирает сам SDK.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaudeClientTest")
class ClaudeClientTest {

    private static final String PROMPT = "Промпт агента";
    private static final String ANSWER_JSON = "{\"answer\":\"используйте индекс для поиска\"}";

    @Mock
    OpenAIClient client;
    @Mock
    ChatService chatService;
    @Mock
    ChatCompletionService completionService;

    private final ClaudeProperties props = new ClaudeProperties("claude-test-model", ReasoningEffort.MEDIUM);

    private ClaudeClient claudeClient;

    @BeforeEach
    void setUp() {
        claudeClient = new ClaudeClient(client, props);
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
    }

    private static <T> StructuredChatCompletionCreateParams<T> anyParams() {
        return any();
    }

    private LlmTrainingReferenceAnswer converse() {
        return claudeClient.converse(PROMPT, List.of("вводная"), List.of(), null,
                LlmTrainingReferenceAnswer.class);
    }

    private void stubText(String text) {
        doReturn(streamOf(text, FinishReason.STOP))
                .when(completionService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    private void stubFinishReason(FinishReason finish) {
        doReturn(streamOf(null, finish))
                .when(completionService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    /**
     * Поток чанков одного ответа; {@code text == null} - ответ без текста.
     */
    @SuppressWarnings("unchecked")
    private static StreamResponse<ChatCompletionChunk> streamOf(String text, FinishReason finish) {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        if (text != null) {
            chunks.add(chunk(List.of(choice(text, null))));
        }
        chunks.add(chunk(List.of(choice(null, finish))));
        chunks.add(chunk(List.of()).toBuilder()
                .usage(CompletionUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build())
                .build());

        StreamResponse<ChatCompletionChunk> response = mock(StreamResponse.class);
        when(response.stream()).thenAnswer(invocation -> chunks.stream());
        return response;
    }

    private static ChatCompletionChunk chunk(List<ChatCompletionChunk.Choice> choices) {
        return ChatCompletionChunk.builder()
                .id("gen_test")
                .created(0)
                .model("claude-test-model")
                .choices(choices)
                .build();
    }

    private static ChatCompletionChunk.Choice choice(String text, FinishReason finish) {
        ChatCompletionChunk.Choice.Delta.Builder delta = ChatCompletionChunk.Choice.Delta.builder();
        if (text != null) {
            delta.content(text);
        }
        return ChatCompletionChunk.Choice.builder()
                .index(0)
                .delta(delta.build())
                .finishReason(Optional.ofNullable(finish))
                .build();
    }

    @Nested
    @DisplayName("Converse")
    class Converse {

        @Test
        @DisplayName("Ответ не разбирается по схеме - LlmException с причиной от SDK, без повтора")
        void throwsWithoutRetryWhenResponseIsNotParseable() {
            // given
            stubText("это вообще не json");

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCauseInstanceOf(OpenAIInvalidDataException.class);
            verify(completionService, times(1))
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Ошибка провайдера не повторяется - вызов ровно один")
        void doesNotRetryOnServiceException() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .build();
            doThrow(serviceException).when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse).isInstanceOf(LlmException.class);
            verify(completionService, times(1))
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Ход k+1: промпт и вводная частями первого сообщения, затем история и новая реплика")
        void sendsDialogAndLastUserOnSubsequentTurn() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText(ANSWER_JSON);

            List<ChatCompletionMessageParam> dialog = List.of(ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam.builder().content("Какой у вас опыт?").build()));

            // when
            var result = claudeClient.converse(PROMPT, List.of("вводная"), dialog, "Три года",
                    LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result).isEqualTo(expected);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<StructuredChatCompletionCreateParams<LlmTrainingReferenceAnswer>> paramsCaptor =
                    ArgumentCaptor.forClass(StructuredChatCompletionCreateParams.class);
            verify(completionService).createStreaming(paramsCaptor.capture());
            var params = paramsCaptor.getValue().rawParams();

            assertThat(params.model().asString()).isEqualTo("claude-test-model");
            assertThat(params.reasoningEffort()).contains(ReasoningEffort.MEDIUM);
            assertThat(params.messages()).hasSize(3);
            assertThat(params.messages().get(0).asUser().content().asArrayOfContentParts())
                    .extracting(part -> part.asText().text())
                    .containsExactly(PROMPT, "вводная");
            assertThat(params.messages().get(1).isAssistant()).isTrue();
            assertThat(params.messages().get(2).asUser().content().asText()).isEqualTo("Три года");
        }

        @Test
        @DisplayName("Сам SDK-вызов кидает OpenAIInvalidDataException - LlmException с исходной причиной")
        void wrapsInvalidDataExceptionFromTheCallItself() {
            // given
            var cause = new OpenAIInvalidDataException("malformed response envelope");
            doThrow(cause).when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("OpenAIServiceException оборачивается в LlmException со статусом")
        void wrapsServiceExceptionWithStatusCode() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .build();
            doThrow(serviceException).when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed with status 400")
                    .hasCause(serviceException);
        }

        @Test
        @DisplayName("Прочий OpenAIException оборачивается в LlmException")
        void wrapsGenericOpenAiException() {
            // given
            var openAiException = new OpenAIException("connection reset");
            doThrow(openAiException).when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(openAiException);
        }

        @Test
        @DisplayName("Ответ остановлен фильтром (CONTENT_FILTER) - LlmException")
        void throwsWhenContentFiltered() {
            // given
            stubFinishReason(FinishReason.CONTENT_FILTER);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason content_filter");
        }

        @Test
        @DisplayName("Ответ упёрся в лимит токенов (LENGTH) - LlmException")
        void throwsWhenModelHitsMaxTokens() {
            // given
            stubFinishReason(FinishReason.LENGTH);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason length");
        }

        @Test
        @DisplayName("В ответе нет текста - LlmException Model not response")
        void throwsWhenResponseHasNoText() {
            // given
            stubFinishReason(FinishReason.STOP);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Model not response");
        }
    }
}
