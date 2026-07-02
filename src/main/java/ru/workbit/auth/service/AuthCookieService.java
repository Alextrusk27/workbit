package ru.workbit.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {
    @Value("${jwt.expiration}")
    private Duration accessExpiration;

    @Value("${app.security.refresh-token-ttl}")
    private Duration refreshExpiration;

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    public static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String REFRESH_PATH = "/api/v1/auth";

    public String buildAccessCookie(String token) {
        return buildCookie(ACCESS_COOKIE_NAME, token, "/", accessExpiration);
    }

    public String buildRefreshCookie(String token) {
        return buildCookie(REFRESH_COOKIE_NAME, token, REFRESH_PATH, refreshExpiration);
    }

    public String clearAccessCookie() {
        return buildCookie(ACCESS_COOKIE_NAME, "", "/", Duration.ZERO);
    }

    public String clearRefreshCookie() {
        return buildCookie(REFRESH_COOKIE_NAME, "", REFRESH_PATH, Duration.ZERO);
    }

    private String buildCookie(String name, String token, String path, Duration maxAge) {
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
