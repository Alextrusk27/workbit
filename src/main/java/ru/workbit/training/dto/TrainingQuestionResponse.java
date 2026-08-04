package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record TrainingQuestionResponse(
        @Schema(description = "Идентификатор вопроса")
        UUID questionId,

        @Schema(description = "Порядковый номер вопроса в сессии (1-based)", example = "1")
        int orderIndex,

        @Schema(description = "Текст вопроса")
        String questionText,

        @Schema(description = "Текст ответа пользователя, null пока не отвечен")
        String answerText,

        @Schema(description = "Оценка ответа от LLM (1-5), до формирования отчёта null", example = "4")
        Integer score,

        @Schema(description = "Текстовый фидбэк по ответу от LLM, до формирования отчёта null")
        String feedback

) {
}
