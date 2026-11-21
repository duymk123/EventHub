package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for logging out (revoking refresh token) at Keycloak.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCLogoutCommand {
    private String refreshToken;
}