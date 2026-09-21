package ru.workbit.billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.billing.dto.BalanceResponse;
import ru.workbit.billing.dto.PaymentCreateRequest;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.service.LimitService;
import ru.workbit.billing.service.PaymentService;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Баланс лимитов, история операций и пополнение баланса")
public class BillingController {

    private final LimitService limitService;
    private final PaymentService paymentService;

    @GetMapping("/quota")
    @Loggable(logResult = true)
    @Operation(summary = "Баланс лимитов",
            description = "Возвращает остаток лимитов, срок их действия и признак хотя бы одной покупки. Просроченный "
                    + "баланс отдаётся нулём без срока. Первое обращение к биллингу зачисляет приветственные лимиты.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Баланс лимитов"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull BalanceResponse> getBalance(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(limitService.getBalance(userDetails.getId()));
    }

    @GetMapping("/usage")
    @Loggable
    @Operation(summary = "Баланс и история операций",
            description = "Возвращает баланс лимитов (как /quota) и историю списаний и зачислений в лимитах, новые "
                    + "первыми. Величина операции всегда положительная, знак задаёт kind.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Баланс и история операций"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull UsageResponse> getUsage(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(limitService.getUsage(userDetails.getId()));
    }

    @PostMapping("/payments")
    @Loggable(logArgs = true)
    @Operation(summary = "Создать платёж",
            description = "Создаёт платёж за пополнение баланса на указанное число лимитов (10-500, кратно 10) и "
                    + "возвращает URL платёжной страницы Робокассы для редиректа. Сумму считает сервер по сетке "
                    + "объёмных скидок.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Платёж создан"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Число лимитов не указано, вне диапазона или не кратно 10",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull PaymentCreateResponse> createPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        return ResponseEntity.ok(paymentService.create(
                userDetails.getId(), request.limits(), userDetails.getEmail()));
    }

    @GetMapping("/payments/{id}")
    @Loggable(logArgs = true, logResult = true)
    @Operation(summary = "Статус платежа",
            description = "Возвращает статус платежа для поллинга после возврата с платёжной страницы. Чужой платёж не "
                    + "отдаётся.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус платежа"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Нет токена или токен недействителен",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Платёж не найден или принадлежит другому пользователю",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull PaymentStatusResponse> getPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(paymentService.get(id, userDetails.getId()));
    }

    @PostMapping(value = "/robokassa/result",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    @Loggable
    @Operation(summary = "Webhook Робокассы (ResultURL)",
            description = "Публичное уведомление Робокассы об оплате (form-urlencoded: OutSum, InvId, SignatureValue). "
                    + "Проверяет подпись и сумму, идемпотентно подтверждает платёж и зачисляет лимиты. "
                    + "Ответ — текст "
                    + "OK{InvId}; на невалидное уведомление — 400, Робокасса повторит доставку.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Платёж подтверждён"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Невалидная подпись, сумма или неизвестный платёж",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull String> robokassaResult(
            @Parameter(hidden = true) @RequestParam Map<String, String> params
    ) {
        return ResponseEntity.ok(paymentService.confirm(params));
    }
}
