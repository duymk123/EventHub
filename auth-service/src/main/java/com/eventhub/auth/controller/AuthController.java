package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.ChangePasswordRequest;
import com.eventhub.auth.dto.request.LoginRequest;
import com.eventhub.auth.dto.request.RefreshRequest;
import com.eventhub.auth.dto.request.RegisterRequest;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.response.AccessTokenResponse;
import com.eventhub.auth.dto.response.TokenResponse;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AUTH-UC01..UC06 theo docs/api/auth-service-openapi.yaml. register/login/refresh/logout không cần token;
 * các API còn lại cần access token hợp lệ, userId lấy từ sub của JWT. C13: refresh token chỉ nằm trong cookie
 * HttpOnly eh_refresh (đặt ở login/refresh, đọc ở refresh/logout); body chỉ có access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    /** POST /api/v1/auth/register → 201, tài khoản mới có role CUSTOMER, mail xác minh được gửi. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST /api/v1/auth/login → accessToken (5 phút) trong body, refresh token trong cookie eh_refresh. */
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletResponse response) {
        TokenResponse token = authService.login(request);
        refreshCookie.write(response, token.refreshToken(), token.refreshExpiresIn());
        return ResponseEntity.ok(AccessTokenResponse.from(token));
    }

    /**
     * POST /api/v1/auth/refresh (không body) → access token mới; refresh token đọc từ cookie, xoay vòng và ghi cookie
     * mới. Thiếu cookie → 401 INVALID_REFRESH_TOKEN. Refresh lỗi thì xóa cookie để frontend về trang đăng nhập.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is missing");
        }
        try {
            TokenResponse token = authService.refresh(new RefreshRequest(refreshToken));
            refreshCookie.write(response, token.refreshToken(), token.refreshExpiresIn());
            return ResponseEntity.ok(AccessTokenResponse.from(token));
        } catch (BusinessException error) {
            refreshCookie.clear(response);
            throw error;
        }
    }

    /**
     * POST /api/v1/auth/logout (không body) → 204, kết thúc phiên Keycloak theo cookie và xóa cookie. Gọi lại khi đã
     * đăng xuất (không còn cookie hoặc phiên đã hết) vẫn trả 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
                                       HttpServletResponse response) {
        refreshCookie.clear(response);
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                authService.logout(new RefreshRequest(refreshToken));
            } catch (BusinessException error) {
                // Phiên đã hết hoặc token không còn hợp lệ: coi như đã đăng xuất.
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** GET /api/v1/auth/me → thông tin tài khoản đang đăng nhập. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.getMe(UUID.fromString(jwt.getSubject())));
    }

    /** POST /api/v1/auth/change-password → 204. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/auth/verify-email/resend → 204, gửi lại mail xác minh. Sau khi bấm link, client gọi
     * /auth/refresh để nhận token có email_verified=true.
     */
    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendVerificationEmail(@AuthenticationPrincipal Jwt jwt) {
        authService.resendVerificationEmail(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
