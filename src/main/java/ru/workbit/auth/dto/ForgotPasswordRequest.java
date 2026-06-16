package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Schema(description = "Email для отправки ссылки сброса пароля", example = "user@example.com")
        @Email @NotBlank String email) {
}
