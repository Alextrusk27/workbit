package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.billing.model.Payment;

public record PaymentStatusResponse(
        @Schema(description = "Статус платежа", example = "PAID")
        Payment.Status status,

        @Schema(description = "Оплачиваемый продукт", example = "PLAN_PRO")
        Payment.Product product
) {
}
