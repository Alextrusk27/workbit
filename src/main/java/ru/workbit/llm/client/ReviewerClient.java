package ru.workbit.llm.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmInterviewAnswer;
import ru.workbit.llm.dto.LlmInterviewReport;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Протокол агента «рецензент» поверх {@link ClaudeClient}: промпт из ресурса и вводная из
 * вакансии и транскрипта собеседования. Вызов одноходовой - беседы с моделью тут нет.
 */
@Component
public class ReviewerClient {
    private static final String TASK = """
            Вакансия:
            %s
            Собеседование:
            %s
            Составь отчёт.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public ReviewerClient(ClaudeClient claude, ObjectMapper objectMapper,
                          @Value("classpath:llm/interview-reviewer.txt") Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Reviewer prompt is not readable", e);
        }
    }

    public LlmInterviewReport review(LlmInterviewVacancy vacancy, List<LlmInterviewAnswer> answers) {
        String task = TASK.formatted(
                objectMapper.writeValueAsString(vacancy),
                objectMapper.writeValueAsString(answers));

        return claude.ask(prompt, task, LlmInterviewReport.class);
    }
}
