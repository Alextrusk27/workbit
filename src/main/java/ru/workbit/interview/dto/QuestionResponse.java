package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record QuestionResponse(
        @Schema(description = "Идентификатор вопроса")
        UUID questionId,

        @Schema(description = "Порядковый индекс вопроса в сессии (1-based)", example = "1")
        int orderIndex,

        @Schema(description = "Текст вопроса")
        String questionText,

        @Schema(description = "Текст ответа пользователя, null пока не отвечен")
        String answerText,

        @Schema(description = "Оценка ответа от LLM (0-10), null пока не выставлена", example = "8")
        Integer score,

        @Schema(description = "Текстовый фидбэк по ответу от LLM, null пока не выставлен")
        String feedback
) {
}
