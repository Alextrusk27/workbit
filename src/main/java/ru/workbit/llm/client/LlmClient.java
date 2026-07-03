package ru.workbit.llm.client;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponsePrompt;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.config.YandexAiProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;

/**
 * Общая обвязка над OpenAIClient: сборка structured-параметров из request-DTO,
 * подстановка переменных в промпт и извлечение типизированного ответа модели.
 */
@Component
@RequiredArgsConstructor
public class LlmClient {
    private final OpenAIClient client;
    private final YandexAiProperties props;
    private final ObjectMapper objectMapper;

    public <T> T call(String agentKey, Object request, Class<T> responseType) {
        StructuredResponseCreateParams<T> params = buildParams(agentKey, request, responseType);
        return client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new LlmException("Model not response"));
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
}
