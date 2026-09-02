package ru.workbit.llm.client;

import com.anthropic.models.messages.MessageParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Протокол агента «интервьюер» поверх {@link ClaudeClient}: промпт из ресурса, вводная с
 * вакансией и сборка диалога из плана и обменов «ответ кандидата - реплика модели».
 * К каждому ответу кандидата код дописывает счётчик заданных основных вопросов, чтобы модель
 * не считала их по истории. Сборка должна быть байт в байт одинаковой между ходами, иначе кэш
 * промпта промахивается.
 */
@Component
public class InterviewerClient {
    private static final String CANDIDATE_ANSWER = "Ответ кандидата: ";
    private static final String MAIN_ASKED = "\nОсновных задано: %d из %d.";
    private static final String MAIN_EXHAUSTED = "\nОсновных задано: %d из %d, новых основных не будет.";
    private static final String OPENING = """
            Вакансия:
            %s
            Вопросов: от %d до %d.
            Составь план собеседования и задай первый вопрос.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public InterviewerClient(ClaudeClient claude, ObjectMapper objectMapper,
                             @Value("classpath:llm/interviewer.txt") Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;
        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Interviewer prompt is not readable", e);
        }
    }

    public LlmInterviewPlan plan(LlmInterviewVacancy vacancy) {
        return claude.converse(prompt, opening(vacancy), List.of(), LlmInterviewPlan.class);
    }

    /**
     * @param plan       план с числом основных вопросов, уже обрезанным кодом в допустимый диапазон
     * @param history    завершённые обмены «ответ кандидата - реплика модели» в порядке беседы
     * @param lastAnswer новый ответ кандидата, на который модель ещё не отвечала
     */
    public LlmInterviewStep next(LlmInterviewVacancy vacancy, LlmInterviewPlan plan,
                                 List<LlmInterviewTurn> history, String lastAnswer) {

        List<MessageParam> dialog = new ArrayList<>(history.size() * 2 + 2);
        dialog.add(assistant(plan));
        int total = plan.questionCount();
        int asked = 1;

        for (LlmInterviewTurn turn : history) {
            dialog.add(user(candidateReply(turn.candidateAnswer(), asked, total)));
            dialog.add(assistant(turn.reply()));

            if (turn.reply().kind() == LlmInterviewStepKind.MAIN) {
                asked++;
            }
        }

        dialog.add(user(candidateReply(lastAnswer, asked, total)));
        return claude.converse(prompt, opening(vacancy), dialog, LlmInterviewStep.class);
    }

    private String opening(LlmInterviewVacancy vacancy) {
        return OPENING.formatted(
                objectMapper.writeValueAsString(vacancy),
                LlmInterviewPlan.MIN_COUNT,
                LlmInterviewPlan.MAX_COUNT
        );
    }

    private static String candidateReply(String answer, int asked, int total) {
        String counter = asked < total ? MAIN_ASKED : MAIN_EXHAUSTED;
        return CANDIDATE_ANSWER + answer + counter.formatted(asked, total);
    }

    private static MessageParam user(String text) {
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(text)
                .build();
    }

    private MessageParam assistant(Object reply) {
        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(objectMapper.writeValueAsString(reply))
                .build();
    }
}
