package ru.workbit.llm.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaudeClientTest")
class ClaudeClientTest {

    private static final String PROMPT = "Промпт агента";

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

    @SuppressWarnings("unchecked")
    private void stubResponse(StructuredTextBlock<LlmTrainingReferenceAnswer> block) {
        StructuredContentBlock<LlmTrainingReferenceAnswer> contentBlock = mock(StructuredContentBlock.class);
        when(contentBlock.text()).thenReturn(Optional.of(block));

        StructuredMessage<LlmTrainingReferenceAnswer> response = mock(StructuredMessage.class);
        when(response.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        when(response.usage()).thenReturn(mock(Usage.class));
        when(response.content()).thenReturn(List.of(contentBlock));

        doReturn(response).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    @SuppressWarnings("unchecked")
    private void stubResponseWithStopReason(StopReason stop) {
        StructuredMessage<LlmTrainingReferenceAnswer> response = mock(StructuredMessage.class);
        when(response.stopReason()).thenReturn(Optional.of(stop));
        when(response.usage()).thenReturn(mock(Usage.class));

        doReturn(response).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
    }

    @Nested
    @DisplayName("Converse")
    class Converse {

        @Test
        @DisplayName("SDK не разобрал structured output, но ответ в markdown-ограде - разбирается ObjectMapper'ом")
        void parsesFencedJsonWhenSdkFailsToParseStructuredOutput() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            String raw = "```json\n{\"answer\":\"используйте индекс для поиска\"}\n```";

            @SuppressWarnings("unchecked")
            StructuredTextBlock<LlmTrainingReferenceAnswer> block = mock(StructuredTextBlock.class);
            when(block.text()).thenThrow(new AnthropicInvalidDataException("not parseable"));
            when(block.rawTextBlock()).thenReturn(TextBlock.builder().text(raw).citations(List.of()).build());
            stubResponse(block);

            // when
            var result = ClaudeClientTest.this.converse();

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("SDK не разобрал ответ, ограды нет - LlmException с исходной причиной")
        void throwsWhenSdkFailsToParseAndThereIsNoFence() {
            // given
            var cause = new AnthropicInvalidDataException("not parseable");

            @SuppressWarnings("unchecked")
            StructuredTextBlock<LlmTrainingReferenceAnswer> block = mock(StructuredTextBlock.class);
            when(block.text()).thenThrow(cause);
            when(block.rawTextBlock()).thenReturn(
                    TextBlock.builder().text("это вообще не json").citations(List.of()).build());
            stubResponse(block);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("Ограда есть, но внутри не разбираемый JSON - LlmException")
        void throwsWhenFencedContentIsNotValidJson() {
            // given
            @SuppressWarnings("unchecked")
            StructuredTextBlock<LlmTrainingReferenceAnswer> block = mock(StructuredTextBlock.class);
            when(block.text()).thenThrow(new AnthropicInvalidDataException("not parseable"));
            when(block.rawTextBlock()).thenReturn(
                    TextBlock.builder().text("```json\nне json\n```").citations(List.of()).build());
            stubResponse(block);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable");
        }

        @Test
        @DisplayName("Нормальный путь не сломан: block.text() отрабатывает, ObjectMapper не трогается")
        void returnsSdkParsedTextAndDoesNotTouchObjectMapper() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");

            @SuppressWarnings("unchecked")
            StructuredTextBlock<LlmTrainingReferenceAnswer> block = mock(StructuredTextBlock.class);
            when(block.text()).thenReturn(expected);
            stubResponse(block);

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

            @SuppressWarnings("unchecked")
            StructuredTextBlock<LlmTrainingReferenceAnswer> block = mock(StructuredTextBlock.class);
            when(block.text()).thenReturn(expected);
            stubResponse(block);

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
            doThrow(cause).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

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
            doThrow(serviceException).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

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
            doThrow(anthropicException).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

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
            stubResponseWithStopReason(StopReason.REFUSAL);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason " + StopReason.REFUSAL);
        }

        @Test
        @DisplayName("Ответ упёрся в лимит токенов (MAX_TOKENS) - LlmException")
        void throwsWhenModelHitsMaxTokens() {
            // given
            stubResponseWithStopReason(StopReason.MAX_TOKENS);

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM stopped with reason " + StopReason.MAX_TOKENS);
        }

        @Test
        @DisplayName("В ответе нет текстового блока - LlmException Model not response")
        void throwsWhenResponseHasNoTextBlock() {
            // given
            @SuppressWarnings("unchecked")
            StructuredMessage<LlmTrainingReferenceAnswer> response = mock(StructuredMessage.class);
            when(response.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
            when(response.usage()).thenReturn(mock(Usage.class));
            when(response.content()).thenReturn(List.of());
            doReturn(response).when(messageService).create(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("Model not response");
        }
    }
}
