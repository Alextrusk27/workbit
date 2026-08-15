package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record PaymentCreateResponse(
        @Schema(description = "Идентификатор платежа для поллинга статуса")
        UUID paymentId,

        @Schema(description = "URL платёжной страницы Робокассы для редиректа")
        String paymentUrl
) {
}
