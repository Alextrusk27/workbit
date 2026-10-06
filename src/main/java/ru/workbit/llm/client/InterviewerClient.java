package ru.workbit.llm.client;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.workbit.exception.LlmException;
import ru.workbit.llm.dto.LlmInterviewPlan;
import ru.workbit.llm.dto.LlmInterviewReply;
import ru.workbit.llm.dto.LlmInterviewStep;
import ru.workbit.llm.dto.LlmInterviewStepKind;
import ru.workbit.llm.dto.LlmInterviewTopic;
import ru.workbit.llm.dto.LlmInterviewTurn;
import ru.workbit.llm.dto.LlmInterviewVacancy;
import tools.jackson.databind.ObjectMapper;

/** Клиент агента «интервьюер»: план и очередной ход беседы. */
@Component
public class InterviewerClient {
    private static final String CANDIDATE_ANSWER = "<answer>%s</answer>";
    private static final String MAIN_ASKED = "\nОсновных задано: %d из %d.";
    private static final String MAIN_EXHAUSTED = "\nОсновных задано: %d из %d, новых основных не будет.";
    private static final String TOPIC_ASKED = " По теме «%s» задано %d из %d.";
    private static final String OPENING = """
            Вакансия:
            %s
            Вопросов: от %d до %d.
            Составь план собеседования и задай первый вопрос.""";

    private final ClaudeClient claude;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public InterviewerClient(ClaudeClient claude, ObjectMapper objectMapper,
                             @Value("${llm.prompts-dir}/interviewer.txt") Resource promptResource) {
        this.claude = claude;
        this.objectMapper = objectMapper;

        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LlmException("Interviewer prompt is not readable", e);
        }
    }

    /** Запрашивает план собеседования и первый вопрос. */
    public LlmInterviewPlan plan(LlmInterviewVacancy vacancy, String askedBefore) {
        LlmInterviewReply reply = claude.converse(prompt, opening(vacancy, askedBefore), List.of(), null,
                LlmInterviewReply.class);

        return new LlmInterviewPlan(
                reply.questionCount() == null ? 0 : reply.questionCount(),
                reply.topics(),
                reply.topic(),
                reply.question());
    }

    /** Запрашивает следующую реплику беседы по плану и истории. */
    public LlmInterviewStep next(LlmInterviewVacancy vacancy, LlmInterviewPlan plan,
                                 List<LlmInterviewTurn> history, String lastAnswer, String askedBefore) {

        List<ChatCompletionMessageParam> dialog = new ArrayList<>(history.size() * 2 + 1);
        dialog.add(assistant(new LlmInterviewReply(LlmInterviewStepKind.MAIN, plan.questionCount(), plan.topics(),
                plan.topic(), plan.question())));
        Map<String, Integer> planned = plannedByTopic(plan);
        Map<String, Integer> askedByTopic = new HashMap<>();
        int total = plan.questionCount();
        int asked = 1;
        String topic = plan.topic();
        countAsked(askedByTopic, topic);

        for (LlmInterviewTurn turn : history) {
            dialog.add(user(candidateReply(turn.candidateAnswer(), asked, total,
                    topicCounter(planned, askedByTopic, topic, asked, total))));
            dialog.add(assistant(turn.reply()));

            if (turn.reply().kind() == LlmInterviewStepKind.MAIN) {
                asked++;
                topic = turn.reply().topic();
                countAsked(askedByTopic, topic);
            }
        }

        return claude.converse(prompt, opening(vacancy, askedBefore), dialog,
                candidateReply(lastAnswer, asked, total,
                        topicCounter(planned, askedByTopic, topic, asked, total)),
                LlmInterviewStep.class);
    }

    /** Собирает вводную: вакансия и вопросы прошлых интервью. */
    private List<String> opening(LlmInterviewVacancy vacancy, String askedBefore) {
        String vacancyBlock = OPENING.formatted(
                objectMapper.writeValueAsString(vacancy),
                LlmInterviewPlan.MIN_COUNT,
                LlmInterviewPlan.MAX_COUNT
        );

        return askedBefore == null || askedBefore.isBlank()
                ? List.of(vacancyBlock)
                : List.of(vacancyBlock, askedBefore);
    }

    private static String candidateReply(String answer, int asked, int total, String topicCounter) {
        String counter = asked < total ? MAIN_ASKED : MAIN_EXHAUSTED;
        return CANDIDATE_ANSWER.formatted(answer) + counter.formatted(asked, total) + topicCounter;
    }

    /** Счётчик по теме текущего основного вопроса. */
    private static String topicCounter(Map<String, Integer> planned, Map<String, Integer> askedByTopic,
                                       String topic, int asked, int total) {
        Integer plannedCount = topic == null ? null : planned.get(topic);
        if (asked >= total || plannedCount == null) {
            return "";
        }
        return TOPIC_ASKED.formatted(topic, askedByTopic.getOrDefault(topic, 0), plannedCount);
    }

    private static Map<String, Integer> plannedByTopic(LlmInterviewPlan plan) {
        if (plan.topics() == null) {
            return Map.of();
        }
        return plan.topics().stream()
                .filter(t -> t.name() != null && t.questions() != null)
                .collect(Collectors.toMap(LlmInterviewTopic::name, LlmInterviewTopic::questions, (a, b) -> a));
    }

    private static void countAsked(Map<String, Integer> askedByTopic, String topic) {
        if (topic != null) {
            askedByTopic.merge(topic, 1, Integer::sum);
        }
    }

    private static ChatCompletionMessageParam user(String text) {
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(text)
                .build());
    }

    private ChatCompletionMessageParam assistant(Object reply) {
        return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                .content(objectMapper.writeValueAsString(reply))
                .build());
    }
}
