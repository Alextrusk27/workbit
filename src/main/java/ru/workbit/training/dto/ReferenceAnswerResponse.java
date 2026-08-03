package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReferenceAnswerResponse(
        @Schema(description = "Эталонный ответ на вопрос")
        String answer
) {
}
