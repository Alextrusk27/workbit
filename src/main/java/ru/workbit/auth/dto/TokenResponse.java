package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "JWT access-токен для авторизации запросов")
        String accessToken,

        @Schema(description = "Refresh-токен для обновления пары токенов")
        String refreshToken
) {
}
