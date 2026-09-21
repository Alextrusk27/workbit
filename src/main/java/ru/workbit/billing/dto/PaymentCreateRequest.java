package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(
        @Schema(description = "Сколько лимитов купить: от 10 до 500, кратно 10", example = "200")
        @NotNull(message = "Limits are required")
        Integer limits
) {
}
