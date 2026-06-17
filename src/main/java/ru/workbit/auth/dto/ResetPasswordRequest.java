package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

public record ResetPasswordRequest(
        @Schema(description = "Токен сброса пароля из письма")
        @NotBlank
        String token,

        @Schema(description = "Новый пароль, минимум 8 символов", example = "N3wP@ssw0rd")
        @NotBlank
        @Size(min = 8)
        String newPassword
) {
    @Override
    public @NotNull String toString() {
        return "ResetPasswordRequest{token=*****, newPassword=*****}";
    }
}
