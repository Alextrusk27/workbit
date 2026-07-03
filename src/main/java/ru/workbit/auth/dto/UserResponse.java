package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record UserResponse(
        @Schema(description = "Email пользователя")
        String email,

        @Schema(description = "Дата регистрации (UTC)")
        Instant created
) {
}
