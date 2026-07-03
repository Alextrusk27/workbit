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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.workbit.auth.dto.*;
import ru.workbit.auth.service.AuthCookieService;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.util.annotation.Loggable;

import static ru.workbit.auth.service.AuthCookieService.REFRESH_COOKIE_NAME;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход, управление паролем и токенами")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;

    @PostMapping("/register")
    @Loggable
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
    @Loggable
    @Operation(summary = "Подтверждение email", description = "Подтверждает email по токену из письма и сразу выдаёт токены в HttpOnly-cookie access_token и refresh_token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email подтверждён, токены выданы в cookie access_token и refresh_token"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Токен недействителен, истёк или уже использован", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        var tokens = authService.verifyEmail(request.token());
        return withAuthCookies(ResponseEntity.ok(), tokens).build();
    }

    @PostMapping("/resend-verification")
    @Loggable
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
    @Loggable
    @Operation(summary = "Вход по email и паролю", description = "Выдаёт пару access/refresh токенов в HttpOnly-cookie access_token и refresh_token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токены выданы в cookie access_token и refresh_token"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Неверные учётные данные, email не подтверждён или пользователь деактивирован", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> login(@RequestBody @Valid LoginRequest request) {
        var tokens = authService.login(request);
        return withAuthCookies(ResponseEntity.ok(), tokens).build();
    }

    @PostMapping("/refresh")
    @Loggable
    @Operation(summary = "Обновление токенов", description = "Обменивает валидный refresh-токен из cookie refresh_token на новую пару токенов, выдаваемых в HttpOnly-cookie access_token и refresh_token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Выдана новая пара токенов в cookie access_token и refresh_token"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Refresh-токен недействителен или отозван", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> refresh(
            @Parameter(description = "Refresh-токен из HttpOnly-cookie refresh_token", required = false)
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        var tokens = authService.refresh(refreshToken);
        return withAuthCookies(ResponseEntity.ok(), tokens).build();
    }

    @PostMapping("/logout")
    @Loggable
    @Operation(summary = "Выход", description = "Отзывает refresh-токен из cookie refresh_token и гасит обе cookie (access_token и refresh_token). Идемпотентен: без cookie тоже возвращает 204 и гасит cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh-токен отозван (если был), cookie access_token и refresh_token сброшены")
    })
    public ResponseEntity<@NotNull Void> logout(
            @Parameter(description = "Refresh-токен из HttpOnly-cookie refresh_token")
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        return withClearCookies(ResponseEntity.noContent()).build();
    }

    @PatchMapping("/change-password")
    @Loggable
    @Operation(summary = "Смена пароля", description = "Меняет пароль текущего пользователя. Штатно аутентификация идёт по access-cookie access_token; заголовок Authorization: Bearer поддержан как fallback для Swagger UI.")
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
    @Loggable
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
    @Loggable
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
    @Loggable
    @Operation(summary = "Удаление аккаунта",
            description = "Деактивирует текущего пользователя (soft delete), отзывает все его refresh-токены и гасит cookie access_token и refresh_token. Штатно аутентификация идёт по access-cookie access_token; заголовок Authorization: Bearer поддержан как fallback для Swagger UI.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Аккаунт деактивирован"),
            @ApiResponse(responseCode = "401", description = "Нет токена или токен недействителен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        authService.deactivateUser(userDetails.getId());

        return withClearCookies(ResponseEntity.noContent()).build();
    }

    @GetMapping("/me")
    @Loggable
    @Operation(summary = "Текущий пользователь",
            description = "Возвращает профиль аутентифицированного пользователя. Штатно аутентификация идёт по access-cookie access_token; заголовок Authorization: Bearer поддержан как fallback для Swagger UI.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль пользователя"),
            @ApiResponse(responseCode = "401", description = "Нет токена или токен недействителен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(authService.getProfile(userDetails.getId()));
    }

    private ResponseEntity.BodyBuilder withAuthCookies(ResponseEntity.BodyBuilder builder, TokenResponse tokens) {
        return builder
                .header(HttpHeaders.SET_COOKIE, cookieService.buildAccessCookie(tokens.accessToken()))
                .header(HttpHeaders.SET_COOKIE, cookieService.buildRefreshCookie(tokens.refreshToken()));
    }

    private ResponseEntity.HeadersBuilder<?> withClearCookies(ResponseEntity.HeadersBuilder<?> builder) {
        return builder
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccessCookie())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefreshCookie());
    }
}
