package ru.workbit.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Ответ интервьюера на первом ходе в схеме structured output: план ({@code questionCount} и
 * {@code topics} - темы с числом вопросов и видом) и первый вопрос. Дальнейшие ходы идут по схеме
 * {@link LlmInterviewStep}: обязательные поля плана на каждом ходе толкали модель к «пустому
 * шаблону» с пустым {@code question}.
 * В историю беседы план уходит этим же record: поля в JSON по алфавиту, как в схеме, которую
 * SDK строит по алфавиту. План в другом порядке полей сбивал первый ход - модель отдавала пустой
 * {@code question}.
 */
@JsonPropertyOrder({"kind", "question", "questionCount", "topic", "topics"})
public record LlmInterviewReply(
        LlmInterviewStepKind kind,
        Integer questionCount,
        List<LlmInterviewTopic> topics,
        String topic,
        String question
) {
}
