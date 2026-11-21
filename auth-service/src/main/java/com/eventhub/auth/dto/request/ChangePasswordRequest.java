package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/change-password. Mật khẩu chỉ chuyển cho Keycloak, không lưu hay log.
 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
