package com.eventhub.auth.client.exception;

/**
 * Exception for Keycloak unavailable errors (5xx, network, timeout).
 * Indicates Keycloak service is temporarily unreachable.
 */
public class KeycloakUnavailableException extends RuntimeException {
    public KeycloakUnavailableException(String message) {
        super(message);
    }

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}