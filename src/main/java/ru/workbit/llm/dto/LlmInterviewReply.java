package ru.workbit.llm.dto;

import java.util.List;

/**
 * Ответ интервьюера в схеме structured output. Схема одна на все ходы беседы, потому что входит в
 * кэшируемый префикс запроса: разные схемы на первом и последующих ходах давали бы вторую запись
 * кэша за интервью. Первый ход заполняет {@code questionCount} и {@code topics} (темы с числом
 * вопросов и видом), дальнейшие - {@code kind}; что именно спрашивать на каждом ходе, говорит промпт.
 */
public record LlmInterviewReply(
        LlmInterviewStepKind kind,
        Integer questionCount,
        List<LlmInterviewTopic> topics,
        String topic,
        String question
) {
}
