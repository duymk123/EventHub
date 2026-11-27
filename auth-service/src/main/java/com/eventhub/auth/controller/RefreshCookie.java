package com.eventhub.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * C13: refresh token chỉ đi trong cookie HttpOnly, SameSite=Strict, Path=/api/v1/auth (chỉ gửi kèm login, refresh,
 * logout). Secure bật khi chạy HTTPS. Không bao giờ ghi giá trị cookie ra log.
 */
@Component
class RefreshCookie {
    static final String NAME = "eh_refresh";
    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    RefreshCookie(@Value("${eventhub.auth.refresh-cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    void write(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, Duration.ofSeconds(maxAgeSeconds)).toString());
    }

    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
