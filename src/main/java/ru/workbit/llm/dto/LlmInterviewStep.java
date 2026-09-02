package ru.workbit.llm.dto;

/**
 * Реплика интервьюера на ходе k. {@code question} пуст только при {@code kind = MAIN},
 * когда основные вопросы исчерпаны, - это сигнал завершения собеседования.
 */
public record LlmInterviewStep(
        LlmInterviewStepKind kind,
        String topic,
        String question
) {
}
