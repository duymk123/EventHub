package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/admin/users/{userId}/reset-password.
 *
 * @param action SEND_EMAIL: Keycloak gửi mail chứa link đặt mật khẩu; SET_TEMPORARY_PASSWORD: Auth Service trả
 *               mật khẩu tạm một lần để ADMIN chuyển cho user.
 */
public record AdminResetPasswordRequest(@NotNull Action action) {

    public enum Action {
        SEND_EMAIL,
        SET_TEMPORARY_PASSWORD
    }
}
