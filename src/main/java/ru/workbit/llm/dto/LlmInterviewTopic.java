package ru.workbit.llm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Тема плана собеседования. */
@JsonPropertyOrder({"kind", "name", "questions"})
public record LlmInterviewTopic(
        String name,
        Integer questions,
        LlmInterviewTopicKind kind
) {
}
