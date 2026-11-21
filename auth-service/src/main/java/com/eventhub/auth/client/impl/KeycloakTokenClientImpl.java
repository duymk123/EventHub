package com.eventhub.auth.client.impl;

import com.eventhub.auth.client.KeycloakTokenClient;
import com.eventhub.auth.client.dto.KCLoginCommand;
import com.eventhub.auth.client.dto.KCLoginResponse;
import com.eventhub.auth.client.dto.KCLogoutCommand;
import com.eventhub.auth.client.dto.KCRefreshCommand;
import com.eventhub.auth.client.exception.KeycloakAuthenticationException;
import com.eventhub.auth.client.exception.KeycloakUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Implementation of KeycloakTokenClient using WebClient.
 * Non-blocking calls to Keycloak token endpoint for PKCE flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakTokenClientImpl implements KeycloakTokenClient {

    private final WebClient keycloakTokenWebClient;

    @org.springframework.beans.factory.annotation.Value("${keycloak.frontend.client-id}")
    private String frontendClientId;

    private static final String TOKEN_ENDPOINT = "/realms/eventhub/protocol/openid-connect/token";
    private static final String LOGOUT_ENDPOINT = "/realms/eventhub/protocol/openid-connect/logout";

    @Override
    public Mono<KCLoginResponse> loginWithCode(KCLoginCommand command) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", command.getCode());
        formData.add("redirect_uri", command.getRedirectUri());
        formData.add("client_id", frontendClientId);

        log.debug("Exchanging auth code for tokens, redirectUri={}", command.getRedirectUri());

        return keycloakTokenWebClient.post()
                .uri(TOKEN_ENDPOINT)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(KCLoginResponse.class)
                .doOnSuccess(response -> log.debug("Token exchange successful"))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Keycloak login failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    if (ex.getStatusCode().is4xxClientError()) {
                        String body = ex.getResponseBodyAsString();
                        if (body != null && body.contains("invalid_grant")) {
                            return new KeycloakAuthenticationException("INVALID_CREDENTIALS", "Invalid authorization code or redirect URI");
                        }
                        return new KeycloakAuthenticationException("INVALID_CREDENTIALS", "Invalid authorization code");
                    }
                    return new KeycloakUnavailableException("Keycloak token endpoint unavailable", ex);
                });
    }

    @Override
    public Mono<KCLoginResponse> refreshToken(KCRefreshCommand command) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", command.getRefreshToken());
        formData.add("client_id", frontendClientId);

        log.debug("Refreshing access token");

        return keycloakTokenWebClient.post()
                .uri(TOKEN_ENDPOINT)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(KCLoginResponse.class)
                .doOnSuccess(response -> log.debug("Token refresh successful"))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Keycloak refresh failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    if (ex.getStatusCode().is4xxClientError()) {
                        String body = ex.getResponseBodyAsString();
                        if (body != null && body.contains("invalid_grant")) {
                            return new KeycloakAuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token invalid or expired");
                        }
                        return new KeycloakAuthenticationException("REFRESH_FAILED", "Token refresh failed");
                    }
                    return new KeycloakUnavailableException("Keycloak token endpoint unavailable", ex);
                });
    }

    @Override
    public Mono<Void> logout(KCLogoutCommand command) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", frontendClientId);
        formData.add("refresh_token", command.getRefreshToken());

        log.debug("Revoking refresh token at Keycloak");

        return keycloakTokenWebClient.post()
                .uri(LOGOUT_ENDPOINT)
                .bodyValue(formData)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.debug("Logout successful"))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("Keycloak logout failed: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new KeycloakUnavailableException("Keycloak logout endpoint unavailable", ex);
                });
    }
}