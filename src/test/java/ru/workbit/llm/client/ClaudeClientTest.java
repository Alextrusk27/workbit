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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionChunk.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.ClaudeProperties;
import ru.workbit.llm.dto.LlmTrainingCaseReview;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReport;
import tools.jackson.core.JacksonException;

/**
 * Ответ модели стабится потоком настоящих SDK-чанков: текст, чанк с причиной остановки и чанк с
 * usage без choices. Текст ответа разбирает мапер самого ClaudeClient.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaudeClientTest")
class ClaudeClientTest {

    private static final String PROMPT = "Промпт агента";
    private static final String ANSWER_JSON = "{\"answer\":\"используйте индекс для поиска\"}";
    private static final JsonValue CACHE_1H = JsonValue.from(Map.of("type", "ephemeral", "ttl", "1h"));

    @Mock
    OpenAIClient client;
    @Mock
    ChatService chatService;
    @Mock
    ChatCompletionService completionService;

    private final ClaudeProperties props = new ClaudeProperties("claude-test-model", ReasoningEffort.MEDIUM, false);

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

    private static ClaudeProperties markedProps() {
        return new ClaudeProperties("claude-test-model", ReasoningEffort.MEDIUM, true);
    }

    private static ChatCompletionMessageParam assistantMessage(String text) {
        return ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder().content(text).build());
    }

    private static JsonValue cacheControl(ChatCompletionContentPart part) {
        return part.asText()._additionalProperties().get("cache_control");
    }

    private List<ChatCompletionMessageParam> sentMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<StructuredChatCompletionCreateParams<LlmTrainingReferenceAnswer>> paramsCaptor =
                ArgumentCaptor.forClass(StructuredChatCompletionCreateParams.class);
        verify(completionService).createStreaming(paramsCaptor.capture());
        return paramsCaptor.getValue().rawParams().messages();
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
    private static StreamResponse<ChatCompletionChunk> streamOf(String text, FinishReason finish) {
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        if (text != null) {
            chunks.add(chunk(List.of(choice(text, null))));
        }
        chunks.add(chunk(List.of(choice(null, finish))));
        chunks.add(chunk(List.of()).toBuilder().usage(usage()).build());
        return streamResponse(chunks);
    }

    /**
     * Поток, в котором причина остановки и usage приходят одним последним чанком.
     */
    private static StreamResponse<ChatCompletionChunk> streamWithFinishAndUsageInOneChunk(String text) {
        List<ChatCompletionChunk> chunks = List.of(
                chunk(List.of(choice(text, null))),
                chunk(List.of(choice(null, FinishReason.STOP))).toBuilder().usage(usage()).build());
        return streamResponse(chunks);
    }

    /**
     * Поток, в котором завершающий чанк с причиной остановки приходит дважды: сначала без usage, затем
     * повтором вместе с usage.
     */
    private static StreamResponse<ChatCompletionChunk> streamWithRepeatedFinishChunk(String text) {
        List<ChatCompletionChunk> chunks = List.of(
                chunk(List.of(choice(text, null))),
                chunk(List.of(choice(null, FinishReason.STOP))),
                chunk(List.of(choice(null, FinishReason.STOP))).toBuilder().usage(usage()).build());
        return streamResponse(chunks);
    }

    @SuppressWarnings("unchecked")
    private static StreamResponse<ChatCompletionChunk> streamResponse(List<ChatCompletionChunk> chunks) {
        StreamResponse<ChatCompletionChunk> response = mock(StreamResponse.class);
        when(response.stream()).thenAnswer(invocation -> chunks.stream());
        return response;
    }

    private static CompletionUsage usage() {
        return CompletionUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build();
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

        private final Logger clientLogger = (Logger) LoggerFactory.getLogger(ClaudeClient.class);
        private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

        @BeforeEach
        void attachLogAppender() {
            logEvents.start();
            clientLogger.addAppender(logEvents);
        }

        @AfterEach
        void detachLogAppender() {
            clientLogger.detachAppender(logEvents);
            logEvents.stop();
        }

        private List<ILoggingEvent> warnings() {
            return logEvents.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
        }

        @Test
        @DisplayName("Шлюз присылает finish_reason и usage одним чанком - ответ разбирается")
        void parsesAnswerWhenFinishReasonAndUsageComeInOneChunk() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            doReturn(streamWithFinishAndUsageInOneChunk(ANSWER_JSON))
                    .when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when
            var result = converse();

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Шлюз повторяет завершающий чанк, второй раз вместе с usage - ответ разбирается")
        void parsesAnswerWhenFinishChunkIsRepeatedWithUsage() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            doReturn(streamWithRepeatedFinishChunk(ANSWER_JSON))
                    .when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when
            var result = converse();

            // then
            assertThat(result).isEqualTo(expected);
            verify(completionService, times(1))
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Ответ не разбирается по схеме - LlmException с причиной от Jackson, без повтора")
        void throwsWithoutRetryWhenResponseIsNotParseable() {
            // given
            stubText("это вообще не json");

            // when / then
            assertThatThrownBy(ClaudeClientTest.this::converse)
                    .isInstanceOf(LlmException.class)
                    .hasMessage("LLM response is not parseable")
                    .hasCauseInstanceOf(JacksonException.class);
            verify(completionService, times(1))
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("Модель дописала поле вне схемы - ответ разбирается, лишнее поле игнорируется")
        void ignoresFieldOutsideSchema() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            stubText("{\"answer\":\"используйте индекс для поиска\",\"hello\":null}");

            // when
            var result = converse();

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Поле вне схемы пишется в WARN с моделью и путём /hello, значения поля в логе нет")
        void warnsWithModelAndPathWhenFieldOutsideSchema() {
            // given
            stubText("{\"answer\":\"используйте индекс для поиска\",\"hello\":\"секретное-значение\"}");

            // when
            converse();

            // then
            assertThat(warnings()).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .isEqualTo("Claude response has field outside schema [model=claude-test-model]: /hello")
                        .doesNotContain("секретное-значение");
            });
        }

        @Test
        @DisplayName("Поле вне схемы во вложенном объекте тоже не валит разбор, в WARN путь /cases/0/extra")
        void ignoresFieldOutsideSchemaInNestedObject() {
            // given
            stubText("{\"cases\":[{\"evaluation\":\"верно\",\"extra\":\"секретное-значение\",\"index\":1,\"score\":4}],"
                    + "\"hello\":null,\"overallFeedback\":\"хорошо\"}");

            // when
            var result = claudeClient.converse(PROMPT, List.of("вводная"), List.of(), null, LlmTrainingReport.class);

            // then
            assertThat(result).isEqualTo(new LlmTrainingReport(
                    List.of(new LlmTrainingCaseReview(1, "верно", 4)), "хорошо"));
            assertThat(warnings()).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactlyInAnyOrder(
                            "Claude response has field outside schema [model=claude-test-model]: /cases/0/extra",
                            "Claude response has field outside schema [model=claude-test-model]: /hello");
        }

        @Test
        @DisplayName("Ответ без лишних полей не пишет WARN")
        void doesNotWarnWhenNoFieldOutsideSchema() {
            // given
            stubText(ANSWER_JSON);

            // when
            converse();

            // then
            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("Схема в запросе по-прежнему запрещает лишние поля: additionalProperties=false")
        void sendsSchemaWithAdditionalPropertiesFalse() {
            // given
            stubText(ANSWER_JSON);

            // when
            converse();

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<StructuredChatCompletionCreateParams<LlmTrainingReferenceAnswer>> paramsCaptor =
                    ArgumentCaptor.forClass(StructuredChatCompletionCreateParams.class);
            verify(completionService).createStreaming(paramsCaptor.capture());
            var jsonSchema = paramsCaptor.getValue().rawParams().responseFormat().orElseThrow()
                    .asJsonSchema().jsonSchema();
            JsonValue schema = jsonSchema._schema().asUnknown().orElseThrow();
            var schemaFields = (Map<?, ?>) schema.asObject().orElseThrow();
            assertThat(schemaFields.get("additionalProperties")).isEqualTo(JsonValue.from(false));
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

        @Test
        @DisplayName("Без меток кэша ни одна часть не несёт cache_control, последняя реплика уходит строкой")
        void sendsNoCacheControlAndPlainLastUserWhenMarksDisabled() {
            // given
            stubText(ANSWER_JSON);
            List<ChatCompletionMessageParam> dialog = List.of(assistantMessage("Какой у вас опыт?"));

            // when
            claudeClient.converse(PROMPT, List.of("вводная", "анкета"), dialog, "Три года",
                    LlmTrainingReferenceAnswer.class);

            // then
            var messages = sentMessages();
            assertThat(messages).hasSize(3);
            assertThat(messages.get(0).asUser().content().asArrayOfContentParts())
                    .extracting(ClaudeClientTest::cacheControl)
                    .hasSize(3)
                    .containsOnlyNulls();
            assertThat(messages.get(2).asUser().content().isText()).isTrue();
            assertThat(messages.get(2).asUser().content().asText()).isEqualTo("Три года");
        }
    }

    @Nested
    @DisplayName("ConverseWithCacheMarks")
    class ConverseWithCacheMarks {

        private ClaudeClient markedClient;

        @BeforeEach
        void setUpMarkedClient() {
            markedClient = new ClaudeClient(client, markedProps());
        }

        @Test
        @DisplayName("Метка на каждой части первого сообщения и на последней реплике, история без изменений")
        void marksOpeningPartsAndLastUserAndKeepsDialogAsIs() {
            // given
            stubText(ANSWER_JSON);
            var assistant = assistantMessage("Какой у вас опыт?");
            List<ChatCompletionMessageParam> dialog = List.of(assistant);

            // when
            markedClient.converse(PROMPT, List.of("вводная", "анкета"), dialog, "Три года",
                    LlmTrainingReferenceAnswer.class);

            // then
            var messages = sentMessages();
            assertThat(messages).hasSize(3);

            var openingParts = messages.get(0).asUser().content().asArrayOfContentParts();
            assertThat(openingParts).extracting(part -> part.asText().text())
                    .containsExactly(PROMPT, "вводная", "анкета");
            assertThat(openingParts).extracting(ClaudeClientTest::cacheControl)
                    .containsExactly(CACHE_1H, CACHE_1H, CACHE_1H);

            assertThat(messages.get(1)).isEqualTo(assistant);
            assertThat(messages.get(1).asAssistant().content().orElseThrow().isText()).isTrue();

            var last = messages.get(2).asUser().content();
            assertThat(last.isText()).isFalse();
            assertThat(last.asArrayOfContentParts()).hasSize(1);
            assertThat(last.asArrayOfContentParts().getFirst().asText().text()).isEqualTo("Три года");
            assertThat(cacheControl(last.asArrayOfContentParts().getFirst())).isEqualTo(CACHE_1H);
        }

        @Test
        @DisplayName("Первый ход без реплики: одно сообщение, все его части с меткой")
        void sendsOnlyMarkedFirstMessageOnFirstTurn() {
            // given
            stubText(ANSWER_JSON);

            // when
            markedClient.converse(PROMPT, List.of("вводная"), List.of(), null, LlmTrainingReferenceAnswer.class);

            // then
            var messages = sentMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.getFirst().asUser().content().asArrayOfContentParts())
                    .hasSize(2)
                    .extracting(ClaudeClientTest::cacheControl)
                    .containsExactly(CACHE_1H, CACHE_1H);
        }
    }

    @Nested
    @DisplayName("Ask")
    class Ask {

        @Test
        @DisplayName("Шлюз повторяет завершающий чанк, второй раз вместе с usage - ответ разбирается")
        void parsesAnswerWhenFinishChunkIsRepeatedWithUsage() {
            // given
            var expected = new LlmTrainingReferenceAnswer("используйте индекс для поиска");
            doReturn(streamWithRepeatedFinishChunk(ANSWER_JSON))
                    .when(completionService)
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());

            // when
            var result = claudeClient.ask(PROMPT, "задача", LlmTrainingReferenceAnswer.class);

            // then
            assertThat(result).isEqualTo(expected);
            verify(completionService, times(1))
                    .createStreaming(ClaudeClientTest.<LlmTrainingReferenceAnswer>anyParams());
        }

        @Test
        @DisplayName("С метками: промпт с меткой, вводная task без метки")
        void marksOnlyPromptWhenMarksEnabled() {
            // given
            stubText(ANSWER_JSON);
            var markedClient = new ClaudeClient(client, markedProps());

            // when
            markedClient.ask(PROMPT, "задача", LlmTrainingReferenceAnswer.class);

            // then
            var messages = sentMessages();
            assertThat(messages).hasSize(1);
            var parts = messages.getFirst().asUser().content().asArrayOfContentParts();
            assertThat(parts).extracting(part -> part.asText().text()).containsExactly(PROMPT, "задача");
            assertThat(cacheControl(parts.get(0))).isEqualTo(CACHE_1H);
            assertThat(cacheControl(parts.get(1))).isNull();
        }

        @Test
        @DisplayName("Без меток: обе части без cache_control")
        void sendsNoCacheControlWhenMarksDisabled() {
            // given
            stubText(ANSWER_JSON);

            // when
            claudeClient.ask(PROMPT, "задача", LlmTrainingReferenceAnswer.class);

            // then
            var messages = sentMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.getFirst().asUser().content().asArrayOfContentParts())
                    .extracting(part -> part.asText().text())
                    .containsExactly(PROMPT, "задача");
            assertThat(messages.getFirst().asUser().content().asArrayOfContentParts())
                    .extracting(ClaudeClientTest::cacheControl)
                    .containsOnlyNulls();
        }
    }
}
