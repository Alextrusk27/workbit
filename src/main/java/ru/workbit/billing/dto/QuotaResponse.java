package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.workbit.billing.model.BillingAccount;

import java.time.Instant;

public record QuotaResponse(
        @Schema(description = "Тариф", example = "FREE")
        BillingAccount.Plan plan,

        @Schema(description = "Окончание оплаченного периода (UTC), null на тарифе Free")
        Instant planExpiresAt,

        @Schema(description = "Остаток интервью по тарифу", example = "1")
        int planInterviewsLeft,

        @Schema(description = "Остаток тренировок по тарифу", example = "3")
        int planTrainingsLeft,

        @Schema(description = "Остаток интервью из докупленных пакетов", example = "0")
        int packInterviewsLeft,

        @Schema(description = "Остаток тренировок из докупленных пакетов", example = "0")
        int packTrainingsLeft
) {
}
