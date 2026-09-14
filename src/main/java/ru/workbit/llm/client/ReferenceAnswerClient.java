package ru.workbit.llm.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswer;
import ru.workbit.llm.dto.LlmTrainingReferenceAnswerRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Протокол агента «автор эталонных ответов тренажёра» поверх {@link ClaudeClient}: промпт из ресурса
 * и вводная из JSON запроса. Уровня во вводной нет: глубину эталона задаёт сам вопрос, составленный
 * на планке своего уровня. Вызов одноходовой.
 */
@Component
public class ReferenceAnswerClient {
    private static final String TASK = """
            %s
            Напиши эталонный ответ.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public ReferenceAnswerClient(ClaudeClient claude, ObjectMapper objectMapper,
                                 @Value("${llm.prompts-dir}/training-reference-answer.txt")
                                 Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Reference answer prompt is not readable", e);
        }
    }

    public LlmTrainingReferenceAnswer create(LlmTrainingReferenceAnswerRequest request) {
        String task = TASK.formatted(objectMapper.writeValueAsString(request));

        return claude.ask(prompt, task, LlmTrainingReferenceAnswer.class);
    }
}
