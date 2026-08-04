package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestCodeRequest(
        @Schema(description = "Email, на который отправить код (незнакомый — заводит нового пользователя)", example = "user@example.com")
        @NotBlank
        @Email
        String email
) {
}
