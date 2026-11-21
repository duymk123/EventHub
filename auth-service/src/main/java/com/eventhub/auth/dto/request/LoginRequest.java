package com.eventhub.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login request (Authorization Code + PKCE flow).
 * Frontend sends authorization code and redirect URI received from Keycloak.
 * Auth Service exchanges code for tokens at Keycloak token endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String redirectUri;
}