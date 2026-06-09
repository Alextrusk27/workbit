package ru.workbit.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.dto.auth.ChangePasswordRequest;
import ru.workbit.dto.auth.LoginRequest;
import ru.workbit.dto.auth.RegistrationRequest;
import ru.workbit.dto.auth.TokenResponse;
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
}
