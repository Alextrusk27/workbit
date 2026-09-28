package ru.workbit.auth.controller;

import static ru.workbit.auth.service.AuthCookieService.REFRESH_COOKIE_NAME;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.workbit.auth.dto.RequestCodeRequest;
import ru.workbit.auth.dto.UserResponse;
import ru.workbit.auth.dto.VerifyCodeRequest;
import ru.workbit.auth.dto.VerifyCodeResponse;
import ru.workbit.security.model.CustomUserDetails;

@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Вход по коду из письма и управление токенами")
public interface AuthApi {

    @Operation(summary = "Запрос кода входа",
            description = "Отправляет код входа на email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Код отправлен"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "403", description = "Капча не пройдена"),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов")
    })
    @PostMapping("/request-code")
    ResponseEntity<Void> requestCode(@RequestBody @Valid RequestCodeRequest request,
                                     HttpServletRequest httpRequest);

    @Operation(summary = "Вход по коду", description = "Проверяет код и выдаёт токены.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вход выполнен"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос"),
            @ApiResponse(responseCode = "401", description = "Код неверен, истёк или попытки исчерпаны"),
            @ApiResponse(responseCode = "429", description = "Слишком много запросов")
    })
    @PostMapping("/verify-code")
    ResponseEntity<VerifyCodeResponse> verifyCode(@RequestBody @Valid VerifyCodeRequest request,
                                                  HttpServletRequest httpRequest);

    @Operation(summary = "Обновление токенов", description = "Меняет refresh_token из cookie на новую пару токенов.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токены обновлены"),
            @ApiResponse(responseCode = "401", description = "Refresh-токена нет или он недействителен")
    })
    @PostMapping("/refresh")
    ResponseEntity<?> refresh(
            @Parameter(description = "Refresh-токен из cookie")
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) @Nullable String refreshToken
    );

    @Operation(summary = "Выход", description = "Отзывает refresh_token и сбрасывает cookie. Идемпотентен.")
    @ApiResponse(responseCode = "204", description = "Выход выполнен")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @Parameter(description = "Refresh-токен из cookie")
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) @Nullable String refreshToken
    );

    @Operation(summary = "Удаление аккаунта", description = "Безвозвратно удаляет пользователя и его данные.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Аккаунт удалён")
    @DeleteMapping("/delete")
    ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );

    @Operation(summary = "Текущий пользователь")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Профиль пользователя")
    @GetMapping("/me")
    ResponseEntity<UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    );
}
