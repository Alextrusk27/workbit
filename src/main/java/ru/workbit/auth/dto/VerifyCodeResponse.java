package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyCodeResponse(
        @Schema(description = "true, если это первая успешная авторизация на email")
        boolean newUser,

        @Schema(description = "true, если в этом входе начислены приветственные лимиты")
        boolean welcomeGranted
) {
}
