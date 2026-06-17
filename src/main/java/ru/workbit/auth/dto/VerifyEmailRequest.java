package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @Schema(description = "Токен подтверждения email из письма")
        @NotBlank String token) {
}
