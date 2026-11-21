package com.eventhub.auth.client;

import com.eventhub.auth.client.dto.KCLoginCommand;
import com.eventhub.auth.client.dto.KCLoginResponse;
import com.eventhub.auth.client.dto.KCLogoutCommand;
import com.eventhub.auth.client.dto.KCRefreshCommand;
import reactor.core.publisher.Mono;

/**
 * Interface for Keycloak token endpoint operations (OIDC).
 * Handles: login with authorization code (PKCE), refresh token, logout.
 * Uses WebClient for non-blocking calls.
 */
public interface KeycloakTokenClient {
    /**
     * Exchange authorization code for tokens (PKCE flow).
     * POST /realms/{realm}/protocol/openid-connect/token
     */
    Mono<KCLoginResponse> loginWithCode(KCLoginCommand command);

    /**
     * Refresh access token using refresh token.
     * POST /realms/{realm}/protocol/openid-connect/token
     */
    Mono<KCLoginResponse> refreshToken(KCRefreshCommand command);

    /**
     * Revoke refresh token (logout).
     * POST /realms/{realm}/protocol/openid-connect/logout
     */
    Mono<Void> logout(KCLogoutCommand command);
}