package ru.workbit.llm.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmTrainingQuestions;
import ru.workbit.llm.dto.LlmTrainingQuestionsRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Протокол агента «составитель вопросов тренажёра» поверх {@link ClaudeClient}: промпт из ресурса и
 * вводная из JSON запроса. Уровень приходит полем запроса, а не отдельным агентом на грейд: планки
 * всех уровней живут в одном промпте. Вызов одноходовой.
 */
@Component
public class QuestionGeneratorClient {
    private static final String TASK = """
            %s
            Составь вопросы.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public QuestionGeneratorClient(ClaudeClient claude, ObjectMapper objectMapper,
                                   @Value("${llm.prompts-dir}/training-question-generator.txt")
                                   Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Question generator prompt is not readable", e);
        }
    }

    public LlmTrainingQuestions generate(LlmTrainingQuestionsRequest request) {
        String task = TASK.formatted(objectMapper.writeValueAsString(request));

        return claude.ask(prompt, task, LlmTrainingQuestions.class);
    }
}
