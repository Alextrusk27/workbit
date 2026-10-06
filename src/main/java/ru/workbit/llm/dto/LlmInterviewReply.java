package ru.workbit.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/** Ответ интервьюера на первом ходе: план и первый вопрос. */
@JsonPropertyOrder({"kind", "question", "questionCount", "topic", "topics"})
public record LlmInterviewReply(
        LlmInterviewStepKind kind,
        Integer questionCount,
        List<LlmInterviewTopic> topics,
        String topic,
        String question
) {
}
