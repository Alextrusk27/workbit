package ru.workbit.llm.client;

import com.anthropic.models.messages.MessageParam;
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

/**
 * Протокол агента «интервьюер» поверх {@link ClaudeClient}: промпт из ресурса, вводная с
 * вакансией и уже заданными в прошлых интервью вопросами, сборка диалога из плана и обменов
 * «ответ кандидата - реплика модели».
 * К каждому ответу кандидата код дописывает счётчики заданных основных вопросов - общий и по
 * теме текущего вопроса, чтобы модель не считала их по истории и не теряла темы плана. Сборка
 * должна быть байт в байт одинаковой между ходами, иначе кэш промпта промахивается.
 */
@Component
public class InterviewerClient {
    private static final String CANDIDATE_ANSWER = "Ответ кандидата: ";
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

    /**
     * Просит у модели план собеседования и первый вопрос.
     *
     * @param askedBefore блок с вопросами прошлых интервью по этой вакансии; null, когда их не было
     */
    public LlmInterviewPlan plan(LlmInterviewVacancy vacancy, String askedBefore) {
        LlmInterviewReply reply = claude.converse(prompt, opening(vacancy, askedBefore), List.of(), null,
                LlmInterviewReply.class);

        return new LlmInterviewPlan(
                reply.questionCount() == null ? 0 : reply.questionCount(),
                reply.topics(),
                reply.topic(),
                reply.question());
    }

    /**
     * Запрашивает у модели следующий шаг интервью с учётом плана и истории беседы.
     *
     * @param plan        план с числом основных вопросов, уже обрезанным кодом в допустимый диапазон
     * @param history     завершённые обмены «ответ кандидата - реплика модели» в порядке беседы
     * @param lastAnswer  новый ответ кандидата, на который модель ещё не отвечала
     * @param askedBefore тот же блок, что ушёл в {@link #plan}: вводная между ходами не меняется
     */
    public LlmInterviewStep next(LlmInterviewVacancy vacancy, LlmInterviewPlan plan,
                                 List<LlmInterviewTurn> history, String lastAnswer, String askedBefore) {

        List<MessageParam> dialog = new ArrayList<>(history.size() * 2 + 1);
        dialog.add(assistant(plan));
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

        LlmInterviewReply reply = claude.converse(prompt, opening(vacancy, askedBefore), dialog,
                candidateReply(lastAnswer, asked, total,
                        topicCounter(planned, askedByTopic, topic, asked, total)),
                LlmInterviewReply.class);

        return new LlmInterviewStep(reply.kind(), reply.topic(), reply.question());
    }

    /**
     * Вводная блоками: вакансия и, если прошлые интервью были, вопросы из них. Отдельным блоком,
     * а не приклейкой к вакансии, чтобы кэш вакансии переживал смену списка от сессии к сессии.
     */
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
        return CANDIDATE_ANSWER + answer + counter.formatted(asked, total) + topicCounter;
    }

    /**
     * Счётчик по теме текущего основного вопроса. Пуст, когда новых основных не будет, когда
     * у плана нет структурных тем (легаси-сессии) и когда модель ушла на тему вне плана.
     */
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
