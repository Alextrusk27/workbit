package ru.workbit.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.workbit.auth.dto.RequestCodeRequest;
import ru.workbit.auth.dto.TokenResponse;
import ru.workbit.auth.dto.UserResponse;
import ru.workbit.auth.dto.VerifyCodeRequest;
import ru.workbit.auth.dto.VerifyCodeResponse;
import ru.workbit.auth.service.AuthCookieService;
import ru.workbit.auth.service.AuthService;
import ru.workbit.exception.dto.ApiError;
import ru.workbit.security.config.RateLimitProperties;
import ru.workbit.security.model.CustomUserDetails;
import ru.workbit.security.service.CaptchaService;
import ru.workbit.security.service.RateLimiterService;
import ru.workbit.util.ClientIp;
import ru.workbit.util.annotation.Loggable;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final AuthCookieService cookieService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final CaptchaService captchaService;

    @Override
    @Loggable
    public ResponseEntity<Void> requestCode(RequestCodeRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("request-code:" + ClientIp.from(httpRequest));
        captchaService.validate(request.captchaToken(), ClientIp.from(httpRequest));
        authService.requestCode(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @Loggable
    public ResponseEntity<VerifyCodeResponse> verifyCode(VerifyCodeRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("verify-code:" + ClientIp.from(httpRequest), rateLimitProperties.verifyCode());
        var result = authService.verifyCode(request);
        return withAuthCookies(ResponseEntity.ok(), result.tokens())
                .body(new VerifyCodeResponse(result.newUser(), result.welcomeGranted()));
    }

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<?> refresh(@Nullable String refreshToken) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Bad credentials", List.of("Refresh token missing")));
        }
        var tokens = authService.refresh(refreshToken);
        return withAuthCookies(ResponseEntity.ok(), tokens).build();
    }

    @Override
    @Loggable
    public ResponseEntity<Void> logout(@Nullable String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        return withClearCookies(ResponseEntity.noContent()).build();
    }

    @Override
    @Loggable
    public ResponseEntity<Void> deleteAccount(CustomUserDetails userDetails) {
        authService.deleteUser(userDetails.getId());

        return withClearCookies(ResponseEntity.noContent()).build();
    }

    @Override
    @Loggable(level = "DEBUG")
    public ResponseEntity<UserResponse> me(CustomUserDetails userDetails) {
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
