package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NormalizeInputRequest(
        @Schema(description = "Профессия, введённая пользователем вручную", example = "джава дев")
        @NotBlank
        @Size(max = 100)
        String profession,

        @Schema(description = "Тема тренировки, введённая вручную; null или пустая - распознаётся только профессия", example = "спринг бут")
        @Size(max = 100)
        String topic
) {
}
