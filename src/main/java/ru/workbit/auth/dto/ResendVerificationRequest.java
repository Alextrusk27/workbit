package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @Schema(description = "Email для повторной отправки письма подтверждения", example = "user@example.com")
        @Email @NotBlank String email) {
}
