package ru.workbit.llm.dto;

/**
 * Реплика интервьюера на ходе k. {@code question} пуст при {@code kind = MAIN}, когда основные
 * вопросы исчерпаны, - это сигнал штатного завершения собеседования; у {@code END} там
 * прощальная реплика, которую увидит кандидат.
 */
public record LlmInterviewStep(
        LlmInterviewStepKind kind,
        String topic,
        String question
) {
}
