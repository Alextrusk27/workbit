package ru.workbit.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.billing.dto.QuotaResponse;
import ru.workbit.billing.service.QuotaService;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Тариф и остатки квот")
public class BillingController {

    private final QuotaService quotaService;

    @GetMapping("/quota")
    @Loggable(logResult = true)
    @Operation(summary = "Текущий тариф и остатки квот",
            description = "Возвращает эффективное состояние тарифа пользователя: план, окончание оплаченного периода и остатки интервью/тренировок раздельно по подписке и докупленным пакетам. Истёкший платный тариф отдаётся как FREE с нулевыми подписочными остатками; пакетные остатки не сгорают.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тариф и остатки квот"),
            @ApiResponse(responseCode = "401", description = "Нет токена или токен недействителен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull QuotaResponse> getQuota(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(quotaService.getQuota(userDetails.getId()));
    }
}
