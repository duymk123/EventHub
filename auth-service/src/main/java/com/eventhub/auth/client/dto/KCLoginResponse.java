package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Keycloak token endpoint.
 * Contains access_token, refresh_token, expires_in, token_type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCLoginResponse {
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    private Integer refreshExpiresIn;
    private String tokenType;
    private String scope;
}