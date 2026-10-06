package ru.workbit.llm.client;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletion.Choice.FinishReason;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.ClaudeProperties;

/** Клиент Claude-агентов через OpenAI-совместимый маршрут шлюза. */
@Slf4j
@Component
public class ClaudeClient {
    private static final long MAX_TOKENS = 32_000L;
    private static final JsonValue CACHE_1H = JsonValue.from(Map.of("type", "ephemeral", "ttl", "1h"));
    private static final ChatCompletionStreamOptions WITH_USAGE = ChatCompletionStreamOptions.builder()
            .includeUsage(true)
            .build();

    private final OpenAIClient client;
    private final ClaudeProperties props;

    public ClaudeClient(@Qualifier("gatewayClaudeClient") OpenAIClient client, ClaudeProperties props) {
        this.client = client;
        this.props = props;
    }

    /** Многоходовая беседа: ответ модели на последнюю реплику. */
    public <T> T converse(String prompt, List<String> opening, List<ChatCompletionMessageParam> dialog,
                          String lastUser, Class<T> responseType) {

        List<ChatCompletionMessageParam> messages = new ArrayList<>(dialog.size() + 2);
        List<ChatCompletionContentPart> openingParts = new ArrayList<>(opening.size() + 1);

        openingParts.add(marked(prompt));
        opening.forEach(part -> openingParts.add(marked(part)));

        messages.add(user(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(openingParts)));
        messages.addAll(dialog);

        if (lastUser != null) {
            messages.add(user(props.cacheMarks()
                    ? ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(List.of(marked(lastUser)))
                    : ChatCompletionUserMessageParam.Content.ofText(lastUser)));
        }

        return sendStreaming(messages, responseType);
    }

    /** Одноходовой вызов: промпт и вводная, ответ по схеме. */
    public <T> T ask(String prompt, String task, Class<T> responseType) {
        ChatCompletionMessageParam message = user(
                ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(List.of(marked(prompt), text(task))));

        return sendStreaming(List.of(message), responseType);
    }

    /** Отправляет запрос стримом и собирает ответ. */
    private <T> T sendStreaming(List<ChatCompletionMessageParam> messages, Class<T> responseType) {
        StructuredChatCompletionCreateParams<T> params = buildParams(messages, responseType);
        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        Set<Long> finished = new HashSet<>();

        StructuredChatCompletion<T> response = call(() -> {
            try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
                stream.stream().forEach(chunk -> accumulate(accumulator, finished, chunk));
            }
            return accumulator.chatCompletion(responseType);
        });

        response.usage().ifPresent(this::logUsage);
        return result(response);
    }

    private <T> StructuredChatCompletion<T> call(Supplier<StructuredChatCompletion<T>> request) {
        try {
            return request.get();

        } catch (OpenAIServiceException e) {
            log.error("Claude call failed [model={}]: status={}, code={}, type={}, body={}",
                    props.model(), e.statusCode(), e.code().orElse(null), e.type().orElse(null), e.body());
            throw new LlmException("LLM call failed with status %d".formatted(e.statusCode()), e);

        } catch (OpenAIException e) {
            log.error("Claude call failed [model={}]", props.model(), e);
            throw new LlmException("LLM call failed", e);
        }
    }

    private <T> T result(StructuredChatCompletion<T> response) {
        StructuredChatCompletion.Choice<T> choice = response.choices().stream()
                .findFirst()
                .orElseThrow(() -> new LlmException("Model not response"));

        FinishReason finish = choice.finishReason();
        if (FinishReason.CONTENT_FILTER.equals(finish) || FinishReason.LENGTH.equals(finish)) {
            log.error("Claude stopped abnormally [model={}, finishReason={}]", props.model(), finish);
            throw new LlmException("LLM stopped with reason " + finish);
        }

        log.debug("Claude raw response [model={}]: {}", props.model(), choice.message().rawMessage().content());
        try {
            return choice.message().content().orElseThrow(() -> new LlmException("Model not response"));
        } catch (OpenAIInvalidDataException e) {
            log.error("Claude response is not parseable [model={}]", props.model(), e);
            throw new LlmException("LLM response is not parseable", e);
        }
    }

    private <T> StructuredChatCompletionCreateParams<T> buildParams(List<ChatCompletionMessageParam> messages,
                                                                     Class<T> responseType) {
        return ChatCompletionCreateParams.builder()
                .model(props.model())
                .maxCompletionTokens(MAX_TOKENS)
                .reasoningEffort(props.effort())
                .streamOptions(WITH_USAGE)
                .messages(messages)
                .responseFormat(responseType)
                .build();
    }

    /** Передаёт чанк в аккумулятор SDK, приводя завершающие чанки шлюза к формату OpenAI. */
    private static void accumulate(ChatCompletionAccumulator accumulator, Set<Long> finished,
                                   ChatCompletionChunk chunk) {
        if (chunk.choices().isEmpty()) {
            accumulator.accumulate(chunk);
            return;
        }
        List<ChatCompletionChunk.Choice> fresh = chunk.choices().stream()
                .filter(choice -> !finished.contains(choice.index()))
                .toList();
        fresh.stream()
                .filter(choice -> choice.finishReason().isPresent())
                .forEach(choice -> finished.add(choice.index()));

        if (!fresh.isEmpty()) {
            accumulator.accumulate(chunk.toBuilder().choices(fresh).usage(Optional.empty()).build());
        }
        if (chunk.usage().isPresent()) {
            accumulator.accumulate(chunk.toBuilder().choices(List.of()).build());
        }
    }

    private ChatCompletionContentPart marked(String text) {
        if (!props.cacheMarks()) {
            return text(text);
        }
        return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
                .text(text)
                .putAdditionalProperty("cache_control", CACHE_1H)
                .build());
    }

    private static ChatCompletionMessageParam user(ChatCompletionUserMessageParam.Content content) {
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(content)
                .build());
    }

    private static ChatCompletionContentPart text(String text) {
        return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
                .text(text)
                .build());
    }

    /** Пишет в лог расход токенов и стоимость вызова. */
    private void logUsage(CompletionUsage usage) {
        log.info("Claude usage [model={}]: input={}, output={}, reasoning={}, cacheRead={}, cacheWrite={}, cost={}",
                props.model(), usage.promptTokens(), usage.completionTokens(),
                usage.completionTokensDetails().flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                        .orElse(0L),
                usage.promptTokensDetails().flatMap(CompletionUsage.PromptTokensDetails::cachedTokens).orElse(0L),
                usage.promptTokensDetails().map(d -> d._additionalProperties().get("cache_write_tokens"))
                        .orElse(null),
                usage._additionalProperties().get("cost"));
    }
}
