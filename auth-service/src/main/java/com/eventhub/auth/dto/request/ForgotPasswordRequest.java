package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/forgot-password. Luôn trả 204 dù email có tồn tại hay không (chống dò tài khoản).
 */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
}
