package ru.workbit.llm.dto;

/**
 * Вид темы плана собеседования: ядро вакансии (на него уходит не меньше половины основных
 * вопросов), обычная тема или тема про отношение к самой работе (не больше одной на план).
 * Доли и лимиты проверяет код в {@code InterviewService.requestPlan}.
 */
public enum LlmInterviewTopicKind {
    CORE, STANDARD, SOFT
}
