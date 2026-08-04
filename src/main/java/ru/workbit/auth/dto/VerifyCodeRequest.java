package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jetbrains.annotations.NotNull;

public record VerifyCodeRequest(
        @Schema(description = "Email, на который отправлен код", example = "user@example.com")
        @NotBlank
        @Email
        String email,

        @Schema(description = "Шестизначный код из письма", example = "123456")
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code
) {
    @Override
    @NotNull
    public String toString() {
        return "VerifyCodeRequest{email=%s, code=*****}".formatted(email);
    }
}
