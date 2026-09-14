package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonMissing;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStopEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.AnthropicProperties;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import tools.jackson.databind.ObjectMapper;

/**
 * Ответ модели стабится потоком настоящих SDK-событий в форме реселлера: {@code message_delta}
 * без {@code usage}, так что стоп-причина читается обходным путём клиента, а текст ответа
 * разбирает сам SDK - случаи «SDK не разобрал» кормятся сырым текстом, а не моком блока.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaudeClientTest")
class ClaudeClientTest {

    private static final String PROMPT = "Промпт агента";
    private static final String ANSWER_JSON = "{\"answer\":\"используйте индекс для поиска\"}";

    @Mock
    AnthropicClient client;
    @Mock
    MessageService messageService;

    private final AnthropicProperties props = new AnthropicProperties(
            "claude-test-model", OutputConfig.Effort.MEDIUM);
    private final ObjectMapper objectMapper = spy(new ObjectMapper());

    private ClaudeClient claudeClient;

    @BeforeEach
    void setUp() {
        claudeClient = new ClaudeClient(client, props, objectMapper);
        when(client.messages()).thenReturn(messageService);
    }

    private static <T> StructuredMessageCreateParams<T> anyParams() {
        return any();
    }

    private LlmTrainingReferenceAnswer converse() {
        return claudeClient.converse(PROMPT, List.of("вводная"), List.<MessageParam>of(), null,
                LlmTrainingReferenceAnswer.class);
    }

    private void stubText(String text) {
        doReturn(streamOf(text, StopReason.END_TURN))
                .when(messageService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    private void stubStopReason(StopReason stop) {
        doReturn(streamOf(null, stop))
                .when(messageService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    /**
     * Поток событий одного ответа; {@code text == null} - ответ без текстового блока.
     */
    @SuppressWarnings("unchecked")
    private static StreamResponse<RawMessageStreamEvent> streamOf(String text, StopReason stop) {
        List<RawMessageStreamEvent> events = new ArrayList<>();
        events.add(RawMessageStreamEvent.ofMessageStart(Message.builder()
                .id("msg_test")
                .model("claude-test-model")
                .content(List.of())
                .container(Optional.empty())
                .stopDetails(Optional.empty())
                .stopReason(Optional.empty())
                .stopSequence(Optional.empty())
                .usage(Usage.builder()
                        .inputTokens(10)
                        .outputTokens(0)
                        .cacheCreation(Optional.empty())
                        .cacheCreationInputTokens(0)
                        .cacheReadInputTokens(0)
                        .inferenceGeo(Optional.empty())
                        .outputTokensDetails(Optional.empty())
                        .serverToolUse(Optional.empty())
                        .serviceTier(Optional.empty())
                        .build())
                .build()));
        if (text != null) {
            events.add(RawMessageStreamEvent.ofContentBlockStart(RawContentBlockStartEvent.builder()
                    .index(0)
                    .contentBlock(TextBlock.builder().text("").citations(List.of()).build())
                    .build()));
            events.add(RawMessageStreamEvent.ofContentBlockDelta(RawContentBlockDeltaEvent.builder()
                    .index(0)
                    .textDelta(text)
                    .build()));
            events.add(RawMessageStreamEvent.ofContentBlockStop(0));
        }
        events.add(RawMessageStreamEvent.ofMessageDelta(RawMessageDeltaEvent.builder()
                .delta(RawMessageDeltaEvent.Delta.builder()
                        .container(Optional.empty())
                        .stopDetails(Optional.empty())
                        .stopReason(stop)
                        .stopSequence(Optional.empty())
                        .build())
                .usage(JsonMissing.of())
                .build()));
        events.add(RawMessageStreamEvent.ofMessageStop(RawMessageStopEvent.builder().build()));

        StreamResponse<RawMessageStreamEvent> response = mock(StreamResponse.class);
        when(response.stream()).thenAnswer(invocation -> events.stream());
        return response;
    }

    @Nested
    @DisplayName("Converse")
    class Converse {

        @Test
        @DisplayName("SDK не разобрал structured output, но ответ в markdown-ограде - разбирается ObjectMapper'ом")
        void parsesFencedJsonWhenSdkFailsToParseStructuredOutput() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText("```json\n" + ANSWER_JSON + "\n```");

            // when
            var result = ClaudeClientTest.this.converse();

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("SDK не разобрал ответ, ограды нет - LlmException с причиной от SDK")
        void throwsWhenSdkFailsToParseAndThereIsNoFence() {
            // given
            stubText("это вообще не json");

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCauseInstanceOf(AnthropicInvalidDataException.class);
        }

        @Test
        @DisplayName("Ограда есть, но внутри не разбираемый JSON - LlmException")
        void throwsWhenFencedContentIsNotValidJson() {
            // given
            stubText("```json\nне json\n```");

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable");
        }

        @Test
        @DisplayName("JSON без ограды, но с пояснением вокруг - разбирается срезом от первой { до последней }")
        void parsesJsonSurroundedByProseWhenSdkFailsToParse() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText("Вот ответ по схеме:\n" + ANSWER_JSON + "\nГотово.");

            // when
            var result = ClaudeClientTest.this.converse();

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Неразбираемый ответ перезапрашивается один раз: повтор вернул JSON - результат его")
        void retriesOnceWhenResponseIsNotParseable() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            doReturn(streamOf("**answer:**\n\n1. используйте индекс", StopReason.END_TURN),
                    streamOf(ANSWER_JSON, StopReason.END_TURN))
                    .when(messageService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when
            var result = ClaudeClientTest.this.converse();

            // then
            assertThat(result).isEqualTo(expected);
            verify(messageService, times(2)).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Неразбираемый ответ и после повтора - LlmException, вызовов ровно два")
        void throwsAfterSingleRetryWhenResponseStaysUnparseable() {
            // given
            doReturn(streamOf("это вообще не json", StopReason.END_TURN),
                    streamOf("и снова не json", StopReason.END_TURN))
                    .when(messageService).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCauseInstanceOf(AnthropicInvalidDataException.class);
            verify(messageService, times(2)).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Ошибка провайдера не повторяется - вызов ровно один")
        void doesNotRetryOnServiceException() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .body(JsonValue.from(Map.of()))
                    .build();
            doThrow(serviceException).when(messageService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse).isInstanceOf(LlmException.class);
            verify(messageService, times(1)).createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Нормальный путь не сломан: SDK разобрал JSON сам, ObjectMapper не трогается")
        void returnsSdkParsedTextAndDoesNotTouchObjectMapper() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText(ANSWER_JSON);

            // when
            var result = ClaudeClientTest.this.converse();

            // then
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("Ход k+1: история и новая реплика уходят кэшируемым хвостом")
        void sendsDialogAndLastUserOnSubsequentTurn() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText(ANSWER_JSON);

            List<MessageParam> dialog = List.of(
                    MessageParam.builder().role(MessageParam.Role.ASSISTANT).content("Какой у вас опыт?").build());

            // when
            var result = claudeClient.converse(PROMPT, List.of("вводная"), dialog, "Три года",
                    LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Сам SDK-вызов кидает AnthropicInvalidDataException - LlmException с исходной причиной")
        void wrapsAnthropicInvalidDataExceptionFromTheCallItself() {
            // given
            var cause = new AnthropicInvalidDataException("malformed response envelope");
            doThrow(cause).when(messageService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("AnthropicServiceException оборачивается в LlmException со статусом")
        void wrapsAnthropicServiceExceptionWithStatusCode() {
            // given
            var serviceException = BadRequestException.builder()
                    .headers(Headers.builder().build())
                    .body(JsonValue.from(Map.of()))
                    .build();
            doThrow(serviceException).when(messageService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed with status 400")
                    .hasCause(serviceException);
        }

        @Test
        @DisplayName("Прочий AnthropicException оборачивается в LlmException")
        void wrapsGenericAnthropicException() {
            // given
            var anthropicException = new AnthropicException("connection reset");
            doThrow(anthropicException).when(messageService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM call failed")
                    .hasCause(anthropicException);
        }

        @Test
        @DisplayName("Модель отказалась отвечать (REFUSAL) - LlmException")
        void throwsWhenModelRefuses() {
            // given
            stubStopReason(StopReason.REFUSAL);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason " + StopReason.REFUSAL);
        }

        @Test
        @DisplayName("Ответ упёрся в лимит токенов (MAX_TOKENS) - LlmException")
        void throwsWhenModelHitsMaxTokens() {
            // given
            stubStopReason(StopReason.MAX_TOKENS);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason " + StopReason.MAX_TOKENS);
        }

        @Test
        @DisplayName("В ответе нет текстового блока - LlmException Model not response")
        void throwsWhenResponseHasNoTextBlock() {
            // given
            stubStopReason(StopReason.END_TURN);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Model not response");
        }
    }
}
