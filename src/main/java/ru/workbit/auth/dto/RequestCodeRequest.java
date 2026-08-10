package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestCodeRequest(
        @Schema(description = "Email, на который отправить код (незнакомый — заводит нового пользователя)", example = "user@example.com")
        @NotBlank
        @Email
        String email,

        @Schema(description = "Согласие на обработку персональных данных; без него код не выдаётся", example = "true")
        @AssertTrue(message = "Personal data consent is required")
        boolean personalDataConsent
) {
}
