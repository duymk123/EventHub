package com.eventhub.auth.service;

import com.eventhub.auth.dto.request.ForgotPasswordRequest;

/**
 * Khôi phục mật khẩu cho người chưa đăng nhập được. Keycloak sở hữu token đặt lại mật khẩu nên Auth Service
 * không tự sinh hay lưu token; link trong mail trỏ tới Keycloak (KC_HOSTNAME).
 */
public interface PasswordRecoveryService {

    /**
     * Gửi mail đặt lại mật khẩu. Luôn hoàn tất bình thường dù email có tồn tại hay không, để người ngoài không
     * dùng endpoint này dò tài khoản.
     */
    void forgotPassword(ForgotPasswordRequest request);
}
