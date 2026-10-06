package ru.workbit.llm.dto;

/** Реплика интервьюера на очередном ходе. */
public record LlmInterviewStep(
        LlmInterviewStepKind kind,
        String question,
        String topic
) {
}
