package ru.workbit.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.ClientIp;
import ru.workbit.util.annotation.Loggable;

import static ru.workbit.auth.service.AuthCookieService.REFRESH_COOKIE_NAME;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Вход по коду из письма и управление токенами")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @PostMapping("/request-code")
    @Loggable
    @Operation(summary = "Запрос кода входа",
            description = "Отправляет одноразовый шестизначный код на email. Отдельной регистрации нет: если пользователя с таким email ещё не было, он создаётся при первом запросе кода. Код действует 15 минут.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Код отправлен на email"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов с этого IP", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> requestCode(@RequestBody @Valid RequestCodeRequest request,
                                                     HttpServletRequest httpRequest) {
        rateLimiter.check("request-code:" + ClientIp.from(httpRequest));
        authService.requestCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-code")
    @Loggable
    @Operation(summary = "Вход по коду",
            description = "Проверяет код из письма и выдаёт токены в HttpOnly-cookie access_token и refresh_token. Успешный ввод кода подтверждает email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токены выданы в cookie access_token и refresh_token"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Код неверен, истёк или исчерпаны попытки", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов с этого IP", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> verifyCode(@RequestBody @Valid VerifyCodeRequest request,
                                                    HttpServletRequest httpRequest) {
        rateLimiter.check("verify-code:" + ClientIp.from(httpRequest), rateLimitProperties.verifyCode());
        var tokens = authService.verifyCode(request);
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

    @DeleteMapping("/delete")
    @Loggable
    @Operation(summary = "Удаление аккаунта",
            description = "Безвозвратно удаляет текущего пользователя вместе со всеми его данными: сессиями интервью, ответами, отчётами и токенами. Гасит cookie access_token и refresh_token. Штатно аутентификация идёт по access-cookie access_token; заголовок Authorization: Bearer поддержан как fallback для Swagger UI.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Аккаунт и все данные удалены"),
            @ApiResponse(responseCode = "401", description = "Нет токена или токен недействителен", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<@NotNull Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        authService.deleteUser(userDetails.getId());

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
