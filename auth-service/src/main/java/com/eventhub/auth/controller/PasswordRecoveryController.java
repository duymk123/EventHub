package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.ForgotPasswordRequest;
import com.eventhub.auth.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quên mật khẩu — flow 7 trong docs/architecture/auth-keycloak-flows.md.
 *
 * <p>Không có endpoint "reset-password" nhận token: link trong mail trỏ thẳng tới Keycloak và Keycloak tự dựng
 * form đặt mật khẩu mới. Auth Service không giữ hay kiểm chứng được token đó nên không mở endpoint nhận nó.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordRecoveryController {

    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordRecoveryController(PasswordRecoveryService passwordRecoveryService) {
        this.passwordRecoveryService = passwordRecoveryService;
    }

    /** POST /api/v1/auth/forgot-password → luôn 204, kể cả khi email không có tài khoản. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordRecoveryService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }
}
