package ru.workbit.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NormalizeInputRequest(
        @Schema(description = "Навык, введённый пользователем вручную", example = "спринг бут")
        @NotBlank
        @Size(max = 100)
        String skill,

        @Schema(description = "Профессия, введённая пользователем вручную", example = "джава дев")
        @NotBlank
        @Size(max = 100)
        String profession
) {
}
