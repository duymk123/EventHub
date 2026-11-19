package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.ChangePasswordRequest;
import com.eventhub.auth.dto.request.LoginRequest;
import com.eventhub.auth.dto.request.RefreshRequest;
import com.eventhub.auth.dto.request.RegisterRequest;
import com.eventhub.auth.dto.response.TokenResponse;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AUTH-UC01..UC06 theo docs/api/auth-service-openapi.yaml. register/login/refresh/logout không cần token;
 * các API còn lại cần access token hợp lệ, userId lấy từ sub của JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/v1/auth/register → 201, tài khoản mới có role CUSTOMER, mail xác minh được gửi. */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST /api/v1/auth/login → accessToken (5 phút) và refreshToken. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** POST /api/v1/auth/refresh → cặp token mới; refresh token cũ hết hiệu lực (xoay vòng). */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** POST /api/v1/auth/logout → 204, kết thúc phiên tại Keycloak. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
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
