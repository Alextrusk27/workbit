package ru.workbit.llm.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.AnthropicProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Обвязка над AnthropicClient для многоходовой беседы со structured output.
 * Промпт агента идёт первым блоком первого user-сообщения, а не в system, вводная - вторым;
 * метки кэша стоят на обоих, потому что между ходами эти блоки неизменны: промпт общий для всех
 * бесед, вводная - для одной. Дальнейшие реплики метку не несут - подвижная метка на последней
 * реплике давала промах кэша через ход.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {
    private static final long MAX_TOKENS = 16_000L;
    private static final CacheControlEphemeral CACHE_1H = CacheControlEphemeral.builder()
                    .ttl(CacheControlEphemeral.Ttl.TTL_1H).build();

    private final AnthropicClient client;
    private final AnthropicProperties props;

    /**
     * @param prompt       текст промпта агента, байт в байт одинаковый между вызовами
     * @param opening      первая реплика пользователя (вводная задачи)
     * @param dialog       дальнейшие реплики по очереди assistant/user: пустой на первом ходе,
     *                     иначе последней идёт текстовая user-реплика
     * @param responseType record со схемой ответа на этом ходе
     */
    public <T> T converse(String prompt, String opening, List<MessageParam> dialog, Class<T> responseType) {
        StructuredMessage<T> response;

        try {
            response = client.messages().create(buildParams(prompt, opening, dialog, responseType));
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

        logUsage(response.usage());
        StopReason stop = response.stopReason().orElse(null);

        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
            log.error("Claude stopped abnormally [model={}, stopReason={}, details={}]",
                    props.model(), stop, response.stopDetails().orElse(null));
            throw new LlmException("LLM stopped with reason " + stop);
        }

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new LlmException("Model not response"));
    }

    private <T> StructuredMessageCreateParams<T> buildParams(String prompt, String opening,
                                                             List<MessageParam> dialog, Class<T> responseType) {
        List<MessageParam> messages = new ArrayList<>(dialog.size() + 1);

        messages.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(List.of(cached(prompt), cached(opening)))
                .build());
        messages.addAll(dialog);

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

    private void logUsage(Usage usage) {
        log.info("Claude usage [model={}]: input={}, output={}, cacheRead={}, cacheCreation={}",
                props.model(), usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }
}
