package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin reset password request.
 * Admin can either send reset email or set temporary password for user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminResetPasswordRequest {
    public enum Action {
        SEND_EMAIL,
        SET_TEMPORARY_PASSWORD
    }

    @NotNull
    private Action action;

    private String temporaryPassword;
}