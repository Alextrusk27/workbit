package ru.workbit.llm.client;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.OpenAiProperties;
import ru.workbit.llm.dto.LlmInputNormalization;
import ru.workbit.llm.dto.LlmInputNormalizationRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Протокол агента «нормализатор ввода»: промпт первым блоком единственного user-сообщения,
 * вводная - JSON запроса вторым, ответ - structured output по схеме {@link LlmInputNormalization}.
 * Маршрут OpenAI-совместимый, а не Messages API, как у Claude-агентов: не-Claude модели каталога
 * на нативный маршрут не ходят. Размышления выключены - подсказки ждёт печатающий пользователь.
 */
@Slf4j
@Component
public class NormalizerClient {
    private static final long MAX_TOKENS = 1024;

    private final OpenAIClient client;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public NormalizerClient(@Qualifier("gatewayOpenAiClient") OpenAIClient client, OpenAiProperties props,
                            ObjectMapper objectMapper,
                            @Value("${llm.prompts-dir}/input-normalizer-openai.txt") Resource promptResource) {
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Normalizer prompt is not readable", e);
        }
    }

    public LlmInputNormalization normalize(LlmInputNormalizationRequest request) {
        StructuredChatCompletionCreateParams<LlmInputNormalization> params = ChatCompletionCreateParams.builder()
                .model(props.model())
                .maxCompletionTokens(MAX_TOKENS)
                .reasoningEffort(ReasoningEffort.NONE)
                .addUserMessageOfArrayOfContentParts(List.of(
                        text(prompt),
                        text(objectMapper.writeValueAsString(request))))
                .responseFormat(LlmInputNormalization.class)
                .build();

        try {
            return client.chat().completions().create(params).choices().stream()
                    .flatMap(choice -> choice.message().content().stream())
                    .findFirst()
                    .orElseThrow(() -> new LlmException("Model not response"));

        } catch (OpenAIServiceException e) {
            log.error("Normalizer call failed [model={}]: status={}, code={}, type={}, body={}",
                    props.model(), e.statusCode(), e.code().orElse(null), e.type().orElse(null), e.body());
            throw new LlmException("LLM call failed with status %d".formatted(e.statusCode()), e);

        } catch (OpenAIException e) {
            log.error("Normalizer call failed [model={}]", props.model(), e);
            throw new LlmException("LLM call failed", e);
        }
    }

    private static ChatCompletionContentPart text(String text) {
        return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder()
                .text(text)
                .build());
    }
}
