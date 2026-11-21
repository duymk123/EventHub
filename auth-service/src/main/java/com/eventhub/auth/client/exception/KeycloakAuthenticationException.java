package com.eventhub.auth.client.exception;

/**
 * Exception for Keycloak authentication/authorization errors (4xx).
 * Contains error code for mapping to API error responses.
 */
public class KeycloakAuthenticationException extends RuntimeException {
    private final String errorCode;

    public KeycloakAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}