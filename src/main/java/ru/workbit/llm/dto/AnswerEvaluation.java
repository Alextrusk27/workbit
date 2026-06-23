package ru.workbit.llm.dto;

/**
 * Результат оценки ответа кандидата моделью: балл 1-5 и текст обратной связи.
 */
public record AnswerEvaluation(int score, String feedback) {
}
