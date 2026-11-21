package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/register. Mật khẩu chuyển thẳng cho Keycloak, không lưu hay log ở Auth Service.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 20) String phoneNumber
) {
    @Override
    public String toString() {
        // Không đưa mật khẩu vào log khi record bị in ra.
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
