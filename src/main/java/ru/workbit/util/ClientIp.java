package ru.workbit.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] parts = forwarded.split(",");
        return parts[parts.length - 1].trim();
    }
}
