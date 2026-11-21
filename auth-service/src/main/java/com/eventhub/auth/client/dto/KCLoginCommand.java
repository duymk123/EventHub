package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for login with authorization code (PKCE flow).
 * Frontend sends code + redirectUri, Auth Service exchanges with Keycloak token endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCLoginCommand {
    private String code;
    private String redirectUri;
}