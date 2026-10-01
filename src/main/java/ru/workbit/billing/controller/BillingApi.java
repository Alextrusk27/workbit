package ru.workbit.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.workbit.billing.dto.BalanceResponse;
import ru.workbit.billing.dto.PaymentCreateRequest;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.security.model.CustomUserDetails;

@RequestMapping("/api/v1/billing")
@Tag(name = "Billing", description = "Баланс лимитов, история операций и пополнение баланса")
public interface BillingApi {

    @Operation(summary = "Баланс лимитов",
            description = "Возвращает остаток лимитов и срок их действия.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Баланс лимитов")
    @GetMapping("/quota")
    ResponseEntity<BalanceResponse> getBalance(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Баланс и история операций",
            description = "Возвращает баланс лимитов и историю списаний и зачислений в лимитах.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Баланс и история операций")
    @GetMapping("/usage")
    ResponseEntity<UsageResponse> getUsage(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Создать платёж",
            description = "Создаёт платёж на пополнение баланса.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Платёж создан"),
            @ApiResponse(responseCode = "400", description = "Число лимитов не указано, вне диапазона или не кратно 10")
    })
    @PostMapping("/payments")
    ResponseEntity<PaymentCreateResponse> createPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentCreateRequest request
    );

    @Operation(summary = "Статус платежа",
            description = "Возвращает статус платежа для поллинга после оплаты.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус платежа"),
            @ApiResponse(responseCode = "404", description = "Платёж не найден или принадлежит другому пользователю")
    })
    @GetMapping("/payments/{id}")
    ResponseEntity<PaymentStatusResponse> getPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id
    );

    @Operation(summary = "Webhook Робокассы",
            description = "Подтверждает платёж и зачисляет лимиты. Идемпотентен.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Платёж подтверждён"),
            @ApiResponse(responseCode = "400",
                    description = "Не хватает параметра, неверна подпись, платёж не найден или сумма не совпадает")
    })
    @PostMapping(value = "/robokassa/result",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> robokassaResult(
            @Parameter(hidden = true) @RequestParam Map<String, String> params
    );
}
