package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "Refresh-токен, который нужно отозвать")
        @NotBlank
        String refreshToken
) {
}
