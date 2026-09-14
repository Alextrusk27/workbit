package ru.workbit.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import ru.workbit.billing.model.BillingAccount;

public record QuotaResponse(
        @Schema(description = "Тариф", example = "FREE")
        BillingAccount.Plan plan,

        @Schema(description = "Окончание оплаченного периода (UTC), null на тарифе Free")
        Instant planExpiresAt,

        @Schema(description = "Остаток интервью по тарифу", example = "1")
        int planInterviewsLeft,

        @Schema(description = "Остаток тренировок по тарифу; null — безлимит (активный тариф Макс)", example = "3")
        Integer planTrainingsLeft
) {
}
