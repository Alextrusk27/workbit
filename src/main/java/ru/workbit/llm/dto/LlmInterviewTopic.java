package ru.workbit.llm.dto;

/**
 * Тема плана собеседования: название, сколько основных вопросов по ней задать и вид темы.
 * Сумма {@code questions} по плану равна {@code questionCount} - распределение по темам
 * держит код, а не память модели.
 */
public record LlmInterviewTopic(
        String name,
        Integer questions,
        LlmInterviewTopicKind kind
) {
}
