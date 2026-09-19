package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.workbit.billing.model.Payment;

public record PaymentCreateRequest(
        @Schema(description = "Оплачиваемый продукт", example = "PACK_200")
        @NotNull(message = "Product is required")
        Payment.Product product
) {
}
