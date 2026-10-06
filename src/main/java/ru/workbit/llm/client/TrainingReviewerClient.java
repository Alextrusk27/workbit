package ru.workbit.llm.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmTrainingReport;
import ru.workbit.llm.dto.LlmTrainingReportRequest;
import tools.jackson.databind.ObjectMapper;

/** Клиент агента «рецензент тренажёра». */
@Component
public class TrainingReviewerClient {
    private static final String TASK = """
            %s
            Составь отчёт.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public TrainingReviewerClient(ClaudeClient claude, ObjectMapper objectMapper,
                                  @Value("${llm.prompts-dir}/training-reviewer.txt")
                                  Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Training reviewer prompt is not readable", e);
        }
    }

    public LlmTrainingReport review(LlmTrainingReportRequest request) {
        String task = TASK.formatted(objectMapper.writeValueAsString(request));

        return claude.ask(prompt, task, LlmTrainingReport.class);
    }
}
