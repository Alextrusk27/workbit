package ru.workbit.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.auth.service.AuthService;
import ru.workbit.auth.dto.ChangePasswordRequest;
import ru.workbit.auth.dto.LoginRequest;
import ru.workbit.auth.dto.RegistrationRequest;
import ru.workbit.auth.dto.TokenResponse;
import ru.workbit.security.model.CustomUserDetails;

@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<@NotNull TokenResponse> register(@RequestBody @Valid RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<@NotNull TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<@NotNull Void> changePassword(@RequestBody @Valid ChangePasswordRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {

        authService.changePassword(request, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<@NotNull Void> refresh() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<@NotNull Void> logout() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<@NotNull Void> forgotPassword() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<@NotNull Void> resetPassword() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<@NotNull Void> verifyEmail() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<@NotNull Void> resendVerification() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
