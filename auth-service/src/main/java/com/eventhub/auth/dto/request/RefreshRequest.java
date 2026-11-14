package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/refresh và /api/v1/auth/logout.
 */
public record RefreshRequest(@NotBlank String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[***]";
    }
}
