package ru.workbit.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record InterviewQuestionResponse(
        @Schema(description = "Идентификатор вопроса")
        UUID questionId,

        @Schema(description = "Порядковый номер вопроса в сессии (1-based)", example = "1")
        int orderIndex,

        @Schema(description = "Текст вопроса")
        String questionText,

        @Schema(description = "Текст ответа пользователя, null пока не отвечен")
        String answerText,

        @Schema(description = "Оценка ответа от LLM (1-5); null до формирования отчёта", example = "4")
        Integer score,

        @Schema(description = "Текстовый фидбэк по ответу от LLM; null до формирования отчёта")
        String feedback
) {
}
