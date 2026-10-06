package ru.workbit.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Тема плана собеседования: название, сколько основных вопросов по ней задать и вид темы.
 * Сумма {@code questions} по плану равна {@code questionCount} - распределение по темам
 * держит код, а не память модели. Поля в JSON по алфавиту, как в схеме ответа: так план
 * в истории беседы совпадает с тем, что выдала модель.
 */
@JsonPropertyOrder({"kind", "name", "questions"})
public record LlmInterviewTopic(
        String name,
        Integer questions,
        LlmInterviewTopicKind kind
) {
}
