package ru.workbit.llm.dto;

/**
 * Реплика интервьюера на ходе k. {@code kind = MAIN} при исчерпанных основных вопросах - это
 * штатное завершение собеседования, и в {@code question} тогда прощальная реплика, как и у
 * {@code END}; кандидат увидит её в сессии. Пустой текст на этих двух ходах допустим.
 * Порядок компонентов - алфавитный, как в схеме structured output: этим же порядком Jackson
 * сериализует реплики в историю беседы, и грамматика ответа не спорит с примерами из истории.
 */
public record LlmInterviewStep(
        LlmInterviewStepKind kind,
        String question,
        String topic
) {
}
