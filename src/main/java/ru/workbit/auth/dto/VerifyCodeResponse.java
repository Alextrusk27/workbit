package ru.workbit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyCodeResponse(
        @Schema(description = "true — первая успешная авторизация (email подтверждён впервые); "
                + "фронт считает её регистрацией для аналитики")
        boolean newUser,

        @Schema(description = "true — в этом входе начислены приветственные лимиты. На один адрес они "
                + "выдаются один раз, поэтому у вернувшегося после удаления аккаунта newUser = true, "
                + "а welcomeGranted = false")
        boolean welcomeGranted
) {
}
