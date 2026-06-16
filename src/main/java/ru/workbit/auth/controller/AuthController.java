package ru.workbit.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.model.CustomUserDetails;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход, управление паролем и токенами")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Регистрация", description = "Создаёт пользователя и отправляет письмо для подтверждения email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пользователь создан, письмо отправлено"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Email уже используется", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> register(@RequestBody @Valid RegistrationRequest request) {
        authService.register(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Подтверждение email", description = "Подтверждает email по токену из письма и сразу выдаёт токены.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email подтверждён, токены выданы"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен недействителен, истёк или уже использован", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TokenResponse> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Повторная отправка письма подтверждения",
            description = "Если активный пользователь с неподтверждённым email существует, отправляет письмо повторно. Ответ всегда 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Запрос принят"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по email и паролю", description = "Возвращает пару access/refresh токенов.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токены выданы"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Неверные учётные данные, email не подтверждён или пользователь деактивирован", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление токенов", description = "Обменивает валидный refresh-токен на новую пару токенов.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Выдана новая пара токенов"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Refresh-токен недействителен или отозван", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull TokenResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход", description = "Отзывает переданный refresh-токен.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh-токен отозван"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Смена пароля", description = "Меняет пароль текущего пользователя. Требует access-токен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пароль изменён"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Старый пароль неверен или нет токена", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> changePassword(@RequestBody @Valid ChangePasswordRequest request,
                                                        @Parameter(hidden = true)
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {

        authService.changePassword(request, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Запрос сброса пароля",
            description = "Если активный пользователь с таким email существует, отправляет письмо со ссылкой сброса. Ответ всегда 200 (не раскрывает наличие email).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Запрос принят"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.remindPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Сброс пароля", description = "Устанавливает новый пароль по токену из письма и отзывает все refresh-токены пользователя.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Пароль сброшен"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен недействителен, истёк или уже использован", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Удаление аккаунта",
            description = "Деактивирует текущего пользователя (soft delete) и отзывает все его refresh-токены. Требует access-токен.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Аккаунт деактивирован"),
            @ApiResponse(responseCode = "401", description = "Нет токена или токен недействителен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> deleteAccount(@Parameter(hidden = true)
                                                       @AuthenticationPrincipal CustomUserDetails userDetails) {

        authService.deactivateUser(userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
