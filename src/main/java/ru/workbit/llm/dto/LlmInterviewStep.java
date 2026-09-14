package ru.workbit.llm.dto;

/**
 * Реплика интервьюера на ходе k. {@code kind = MAIN} при исчерпанных основных вопросах - это
 * штатное завершение собеседования, и в {@code question} тогда прощальная реплика, как и у
 * {@code END}; кандидат увидит её в сессии. Пустой текст на этих двух ходах допустим.
 */
public record LlmInterviewStep(
        LlmInterviewStepKind kind,
        String topic,
        String question
) {
}
