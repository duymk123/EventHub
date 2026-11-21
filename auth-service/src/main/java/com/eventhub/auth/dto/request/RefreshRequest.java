package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token refresh request.
 * Client sends refresh token to get new access token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {
    @NotBlank
    private String refreshToken;
}