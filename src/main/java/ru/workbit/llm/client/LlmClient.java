package ru.workbit.llm.client;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponsePrompt;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.YandexAiProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Общая обвязка над OpenAIClient: сборка structured-параметров из request-DTO,
 * подстановка переменных в промпт и извлечение типизированного ответа модели.
 * Ответы с CJK-вкраплениями (иероглифы, кана, хангыль — редкий дефект моделей)
 * перезапрашиваются один раз, при повторном срабатывании символы вырезаются.
 * Неразбираемый ответ (модель отдала не JSON) тоже перезапрашивается один раз;
 * если и повтор не разобрался, в лог идёт ERROR с маркером {@value #MODEL_DEGRADED} —
 * признак того, что модель агента в Studio перестала держать формат.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {
    public static final String MODEL_DEGRADED = "LLM_MODEL_DEGRADED";

    private static final Pattern CJK =
            Pattern.compile("[぀-ヿ㐀-䶿一-鿿豈-﫿가-힯]");

    private final OpenAIClient client;
    private final YandexAiProperties props;
    private final ObjectMapper objectMapper;

    public <T> T call(String agentKey, Object request, Class<T> responseType) {
        T result = callParseable(agentKey, request, responseType);
        if (!containsCjk(result)) {
            return result;
        }
        log.warn("LLM response contains CJK characters [agent={}], retrying once", agentKey);
        T retried = callParseable(agentKey, request, responseType);
        if (!containsCjk(retried)) {
            return retried;
        }
        log.warn("LLM response contains CJK characters after retry [agent={}], stripping them", agentKey);
        return stripCjk(retried, responseType);
    }

    private <T> T callParseable(String agentKey, Object request, Class<T> responseType) {
        try {
            return callOnce(agentKey, request, responseType);
        } catch (OpenAIInvalidDataException first) {
            log.warn("LLM response is not parseable [agent={}], retrying once: {}", agentKey, first.getMessage());
            try {
                return callOnce(agentKey, request, responseType);
            } catch (OpenAIInvalidDataException second) {
                log.error("{}: LLM response is not parseable after retry [agent={}, promptId={}] — check the agent model in Studio",
                        MODEL_DEGRADED, agentKey, props.agents().get(agentKey), second);
                throw new LlmException("LLM response is not parseable", second);
            }
        }
    }

    private <T> T callOnce(String agentKey, Object request, Class<T> responseType) {
        log.debug("LLM request [agent={}]: {}", agentKey, objectMapper.writeValueAsString(request));
        StructuredResponseCreateParams<T> params = buildParams(agentKey, request, responseType);
        try {
            return client.responses().create(params).output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new LlmException("Model not response"));
        } catch (OpenAIInvalidDataException e) {
            throw e;
        } catch (OpenAIServiceException e) {
            log.error("LLM call failed [agent={}, promptId={}]: status={}, code={}, type={}, param={}, body={}",
                    agentKey, props.agents().get(agentKey), e.statusCode(),
                    e.code().orElse(null), e.type().orElse(null), e.param().orElse(null), e.body());
            throw new LlmException("LLM call failed with status %d".formatted(e.statusCode()), e);
        } catch (OpenAIException e) {
            log.error("LLM call failed [agent={}, promptId={}]", agentKey, props.agents().get(agentKey), e);
            throw new LlmException("LLM call failed", e);
        }
    }

    private <T> StructuredResponseCreateParams<T> buildParams(String agentKey, Object request, Class<T> responseType) {
        Map<String, Object> vars = objectMapper.convertValue(request, new TypeReference<>() {});

        var variablesBuilder = ResponsePrompt.Variables.builder();
        vars.forEach((k, v) -> {
            Object value = (v instanceof Map || v instanceof Collection) ? objectMapper.writeValueAsString(v) : v;
            variablesBuilder.putAdditionalProperty(k, JsonValue.from(value));
        });

        return ResponseCreateParams.builder()
                .prompt(ResponsePrompt.builder()
                        .id(props.agents().get(agentKey))
                        .variables(variablesBuilder.build())
                        .build())
                .text(responseType)
                .build();
    }

    private boolean containsCjk(Object result) {
        return CJK.matcher(objectMapper.writeValueAsString(result)).find();
    }

    private <T> T stripCjk(T result, Class<T> responseType) {
        String cleaned = CJK.matcher(objectMapper.writeValueAsString(result))
                .replaceAll("")
                .replaceAll(" {2,}", " ");
        return objectMapper.readValue(cleaned, responseType);
    }
}
