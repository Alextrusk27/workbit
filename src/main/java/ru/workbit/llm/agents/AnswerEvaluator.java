package ru.workbit.llm.agents;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponsePrompt;
import com.openai.models.responses.StructuredResponseCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.AnswerEvaluationRequest;
import ru.workbit.llm.config.YandexAiProperties;
import ru.workbit.llm.dto.AnswerEvaluation;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnswerEvaluator {
    private final OpenAIClient client;
    private final YandexAiProperties props;
    private final ObjectMapper objectMapper;

    public AnswerEvaluation evaluateAnswer(AnswerEvaluationRequest request) {
        StructuredResponseCreateParams<AnswerEvaluation> params = createParams(request);
        return client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new LlmException("Модель не вернула ответ"));
    }

    private StructuredResponseCreateParams<AnswerEvaluation> createParams(AnswerEvaluationRequest request) {
        Map<String, Object> vars = objectMapper.convertValue(request, new TypeReference<>() {});

        var variablesBuilder = ResponsePrompt.Variables.builder();
        vars.forEach((k, v) -> variablesBuilder.putAdditionalProperty(k, JsonValue.from(v)));

        return ResponseCreateParams.builder()
                .prompt(ResponsePrompt.builder()
                        .id(props.agents().get("answer-evaluator"))
                        .variables(variablesBuilder.build())
                        .build())
                .text(AnswerEvaluation.class)
                .build();
    }
}
