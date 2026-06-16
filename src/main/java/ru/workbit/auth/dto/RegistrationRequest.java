package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

public record RegistrationRequest(
        @Schema(description = "Email нового пользователя", example = "user@example.com")
        @NotBlank
        @Email
        String email,

        @Schema(description = "Пароль, минимум 8 символов", example = "P@ssw0rd123")
        @NotBlank
        @Size(min = 8)
        String password
) {
    @Override
    @NotNull
    public String toString() {
        return "RegisterRequest{email=%s, password=*****}".formatted(email);
    }
}
