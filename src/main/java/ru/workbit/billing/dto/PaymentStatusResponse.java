package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import ru.workbit.billing.model.Payment;

public record PaymentStatusResponse(
        @Schema(description = "Статус платежа", example = "PAID")
        Payment.Status status,

        @Schema(description = "Сколько лимитов куплено", example = "200")
        int limits,

        @Schema(description = "Сумма платежа в рублях", example = "2400.00")
        BigDecimal amount
) {
}
