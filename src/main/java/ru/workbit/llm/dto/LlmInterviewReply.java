package ru.workbit.llm.dto;

import java.util.List;

/**
 * Ответ интервьюера на первом ходе в схеме structured output: план ({@code questionCount} и
 * {@code topics} - темы с числом вопросов и видом) и первый вопрос. Дальнейшие ходы идут по схеме
 * {@link LlmInterviewStep}: обязательные поля плана на каждом ходе толкали модель к «пустому
 * шаблону» с пустым {@code question}.
 */
public record LlmInterviewReply(
        LlmInterviewStepKind kind,
        Integer questionCount,
        List<LlmInterviewTopic> topics,
        String topic,
        String question
) {
}
