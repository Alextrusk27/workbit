package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record BalanceResponse(
        @Schema(description = "Остаток лимитов; просроченный баланс отдаётся нулём", example = "20")
        int limits,

        @Schema(description = "Срок действия лимитов (UTC); null, если лимитов нет")
        Instant expiresAt,

        @Schema(description = "Была ли хоть одна покупка пакета", example = "false")
        boolean paid
) {
}
