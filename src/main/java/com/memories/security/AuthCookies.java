package com.memories.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Token được giữ trong cookie HttpOnly để JavaScript (kể cả script XSS) không đọc được.
 * - Access token (JWT, ngắn hạn): gửi kèm mọi request /api.
 * - Refresh token (dài hạn): chỉ gửi tới /api/auth (refresh, logout), không lộ ra các API khác.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "memories_auth";
    public static final String REFRESH = "memories_refresh";
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;
    private final Duration accessMaxAge;
    private final Duration refreshMaxAge;

    public AuthCookies(@Value("${app.jwt.cookie-secure}") boolean secure,
                       @Value("${app.jwt.access-expiration-ms}") long accessMs,
                       @Value("${app.jwt.refresh-expiration-ms}") long refreshMs) {
        this.secure = secure;
        this.accessMaxAge = Duration.ofMillis(accessMs);
        this.refreshMaxAge = Duration.ofMillis(refreshMs);
    }

    public String createAccess(String token) {
        return build(ACCESS, token, ACCESS_PATH, accessMaxAge);
    }

    public String createRefresh(String token) {
        return build(REFRESH, token, REFRESH_PATH, refreshMaxAge);
    }

    public String clearAccess() {
        return build(ACCESS, "", ACCESS_PATH, Duration.ZERO);
    }

    public String clearRefresh() {
        return build(REFRESH, "", REFRESH_PATH, Duration.ZERO);
    }

    public Optional<String> readAccess(HttpServletRequest request) {
        return read(request, ACCESS);
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        return read(request, REFRESH);
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()) && !c.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    private String build(String name, String value, String path, Duration age) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(age)
                .build()
                .toString();
    }
}
