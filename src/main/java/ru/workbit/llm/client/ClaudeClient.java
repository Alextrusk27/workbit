package ru.workbit.llm.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.AnthropicProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Обвязка над AnthropicClient для вызовов со structured output.
 * Промпт агента идёт первым блоком первого user-сообщения, а не в system, вводная - вторым.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {
    private static final long MAX_TOKENS = 32_000L;
    private static final CacheControlEphemeral CACHE_1H = CacheControlEphemeral.builder()
                    .ttl(CacheControlEphemeral.Ttl.TTL_1H).build();
    private static final Pattern JSON_FENCE = Pattern.compile("^\\s*```(?:json)?\\s*|\\s*```\\s*$");

    private final AnthropicClient client;
    private final AnthropicProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Многоходовая беседа. Метки кэша стоят на промпте, блоках вводной и новой реплике пользователя:
     * вводная неизменна между ходами, а подвижная метка на хвосте истории оставляет вне кэша только
     * прирост с прошлого хода - без неё вся переписка досылалась бы каждый ход по цене обычного входа.
     * Провайдер держит не больше четырёх меток на запрос, поэтому блоков вводной - не больше двух.
     * Схема ответа входит в кэшируемый префикс, поэтому на всех ходах беседы она должна быть одна.
     *
     * @param prompt       текст промпта агента, байт в байт одинаковый между вызовами
     * @param opening      первая реплика пользователя (вводная задачи) блоками, каждый кэшируется отдельно
     * @param dialog       завершённые реплики по очереди assistant/user, пустой на первом ходе
     * @param lastUser     новая реплика пользователя, на которую отвечает модель; null на первом ходе
     * @param responseType record со схемой ответа, общий для всех ходов беседы
     */
    public <T> T converse(String prompt, List<String> opening, List<MessageParam> dialog, String lastUser,
                          Class<T> responseType) {

        List<MessageParam> messages = new ArrayList<>(dialog.size() + 2);
        List<ContentBlockParam> openingBlocks = new ArrayList<>(opening.size() + 1);

        openingBlocks.add(cached(prompt));
        opening.forEach(block -> openingBlocks.add(cached(block)));

        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(openingBlocks)
                .build());
        messages.addAll(dialog);

        if (lastUser != null) {
            messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(List.of(cached(lastUser)))
                    .build());
        }

        return send(messages, responseType);
    }

    /**
     * Одноходовой вызов, стриминговый. Метка кэша стоит только на промпте: вводная уникальна для
     * вызова, из кэша повторно не читается, а запись в кэш с TTL 1h стоит дороже обычного входа.
     * Стриминг здесь не ради частичного вывода, а против обрыва: отчёт по длинному интервью
     * генерируется больше минуты, а молчащее соединение с провайдером режет NAT.
     *
     * @param prompt       текст промпта агента, байт в байт одинаковый между вызовами
     * @param task         вводная с данными задачи
     * @param responseType record со схемой ответа
     */
    public <T> T ask(String prompt, String task, Class<T> responseType) {
        MessageParam message = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(List.of(cached(prompt), plain(task)))
                .build();

        return sendStreaming(List.of(message), responseType);
    }

    private <T> T send(List<MessageParam> messages, Class<T> responseType) {
        StructuredMessageCreateParams<T> params = buildParams(messages, responseType);
        StructuredMessage<T> response = call(() -> client.messages().create(params));

        logUsage(response.usage());
        return result(response, response.stopReason().orElse(null), responseType);
    }

    /**
     * Обходной путь: провайдер шлёт {@code message_delta} без обязательного по спеке {@code usage}, а
     * {@link MessageAccumulator} читает это поле через {@code getRequired} и падает. Поэтому
     * событие без разбираемого usage идёт мимо аккумулятора, а {@code stop_reason} берётся прямо
     * из него; когда провайдер починит формат, событие снова пойдёт в аккумулятор и выходные
     * токены появятся в логе сами.
     */
    private <T> T sendStreaming(List<MessageParam> messages, Class<T> responseType) {
        StructuredMessageCreateParams<T> params = buildParams(messages, responseType);
        MessageAccumulator accumulator = MessageAccumulator.create();
        AtomicReference<StopReason> stop = new AtomicReference<>();
        AtomicInteger outputChars = new AtomicInteger();

        StructuredMessage<T> response = call(() -> {
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                stream.stream().forEach(event -> {
                    if (event.isMessageDelta() && event.asMessageDelta()._usage().asKnown().isEmpty()) {
                        event.asMessageDelta().delta().stopReason().ifPresent(stop::set);
                        return;
                    }
                    event.contentBlockDelta()
                            .flatMap(block -> block.delta().text())
                            .ifPresent(text -> outputChars.addAndGet(text.text().length()));
                    accumulator.accumulate(event);
                });
            }
            return accumulator.message(responseType);
        });

        logStreamUsage(response.usage(), outputChars.get());
        return result(response, stop.get() != null ? stop.get() : response.stopReason().orElse(null), responseType);
    }

    private <T> StructuredMessage<T> call(Supplier<StructuredMessage<T>> request) {
        try {
            return request.get();

        } catch (AnthropicInvalidDataException e) {
            log.error("Claude response is not parseable [model={}]", props.model(), e);
            throw new LlmException("LLM response is not parseable", e);

        } catch (AnthropicServiceException e) {
            log.error("Claude call failed [model={}]: status={}, type={}, body={}",
                    props.model(), e.statusCode(), e.errorType().orElse(null), e.body());
            throw new LlmException("LLM call failed with status %d".formatted(e.statusCode()), e);

        } catch (AnthropicException e) {
            log.error("Claude call failed [model={}]", props.model(), e);
            throw new LlmException("LLM call failed", e);
        }
    }

    private <T> T result(StructuredMessage<T> response, StopReason stop, Class<T> responseType) {
        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
            log.error("Claude stopped abnormally [model={}, stopReason={}, details={}]",
                    props.model(), stop, response.stopDetails().orElse(null));
            throw new LlmException("LLM stopped with reason " + stop);
        }

        StructuredTextBlock<T> block = response.content().stream()
                .flatMap(content -> content.text().stream())
                .findFirst()
                .orElseThrow(() -> new LlmException("Model not response"));

        try {
            return block.text();
        } catch (AnthropicInvalidDataException e) {
            return parseRawText(block, responseType, e);
        }
    }

    /**
     * Ответ по схеме, который SDK не разобрал: реселлер отдаёт structured output не как грамматику, а
     * обычным текстом, и модель иногда оборачивает JSON в markdown-ограду. Снимаем ограду и разбираем
     * сами - вызов уже оплачен, а у отчёта по интервью он длится минуту.
     */
    private <T> T parseRawText(StructuredTextBlock<T> block, Class<T> responseType,
                               AnthropicInvalidDataException cause) {

        String raw = block.rawTextBlock().text();
        String json = JSON_FENCE.matcher(raw).replaceAll("");

        if (json.equals(raw)) {
            log.error("Claude response is not parseable [model={}]", props.model(), cause);
            throw new LlmException("LLM response is not parseable", cause);
        }

        log.warn("Claude wrapped the structured response in a markdown fence [model={}], unwrapping",
                props.model());
        try {
            return objectMapper.readValue(json, responseType);
        } catch (RuntimeException e) {
            log.error("Claude response is not parseable even unwrapped [model={}]", props.model(), e);
            throw new LlmException("LLM response is not parseable", e);
        }
    }

    private <T> StructuredMessageCreateParams<T> buildParams(List<MessageParam> messages, Class<T> responseType) {
        return MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(MAX_TOKENS)
                .messages(messages)
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .effort(props.effort())
                        .format(responseType)
                        .build())
                .build();
    }

    private static ContentBlockParam cached(String text) {
        return ContentBlockParam.ofText(TextBlockParam.builder()
                .text(text)
                .cacheControl(CACHE_1H)
                .build()
        );
    }

    private static ContentBlockParam plain(String text) {
        return ContentBlockParam.ofText(TextBlockParam.builder()
                .text(text)
                .build()
        );
    }

    private void logUsage(Usage usage) {
        log.info("Claude usage [model={}]: input={}, output={}, cacheRead={}, cacheCreation={}",
                props.model(), usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }

    /**
     * Выходных токенов при стриминге нет: в {@code message_start} вместо них заглушка, а
     * {@code message_delta} с настоящим значением провайдер не шлёт. Вместо них - длина ответа
     * в символах, чтобы цифру нельзя было прочесть как токены; расход выхода меряет батарея
     * нестриминговым прогоном.
     */
    private void logStreamUsage(Usage usage, int outputChars) {
        log.info("Claude usage [model={}]: input={}, outputChars={}, cacheRead={}, cacheCreation={}",
                props.model(), usage.inputTokens(), outputChars,
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }
}
