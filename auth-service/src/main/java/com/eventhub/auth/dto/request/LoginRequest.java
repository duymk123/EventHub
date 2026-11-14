package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/login.
 */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
