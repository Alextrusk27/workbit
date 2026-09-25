package ru.workbit.billing.controller;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.billing.dto.BalanceResponse;
import ru.workbit.billing.dto.PaymentCreateRequest;
import ru.workbit.billing.dto.PaymentCreateResponse;
import ru.workbit.billing.dto.PaymentStatusResponse;
import ru.workbit.billing.dto.UsageResponse;
import ru.workbit.billing.service.LimitService;
import ru.workbit.billing.service.PaymentService;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;

@RestController
@RequiredArgsConstructor
public class BillingController implements BillingApi {

    private final LimitService limitService;
    private final PaymentService paymentService;

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<BalanceResponse> getBalance(CustomUserDetails userDetails) {
        return ResponseEntity.ok(limitService.getBalance(userDetails.getId()));
    }

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<UsageResponse> getUsage(CustomUserDetails userDetails) {
        return ResponseEntity.ok(limitService.getUsage(userDetails.getId()));
    }

    @Override
    @Loggable(logArgs = true)
    public ResponseEntity<PaymentCreateResponse> createPayment(CustomUserDetails userDetails,
                                                                PaymentCreateRequest request) {
        return ResponseEntity.ok(paymentService.create(
                userDetails.getId(), request.limits(), userDetails.getEmail()));
    }

    @Override
    @Loggable(logArgs = true, logResult = true)
    public ResponseEntity<PaymentStatusResponse> getPayment(CustomUserDetails userDetails, UUID id) {
        return ResponseEntity.ok(paymentService.get(id, userDetails.getId()));
    }

    @Override
    @Loggable
    public ResponseEntity<String> robokassaResult(Map<String, String> params) {
        return ResponseEntity.ok(paymentService.confirm(params));
    }
}
