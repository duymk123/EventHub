package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resend email verification request.
 * User provides email to resend verification link.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationRequest {
    @NotBlank
    @Email
    private String email;
}