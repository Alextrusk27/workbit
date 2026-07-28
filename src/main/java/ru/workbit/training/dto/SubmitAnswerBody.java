package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SubmitAnswerBody(
        @Schema(description = "Текст ответа пользователя на вопрос", example = "Использую индексы и explain analyze для оптимизации запросов")
        @NotBlank
        String answerText
) {
}
