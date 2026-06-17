package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "Действующий refresh-токен")
        @NotBlank
        String refreshToken
) {
}
