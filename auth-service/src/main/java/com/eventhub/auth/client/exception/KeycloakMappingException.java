package com.eventhub.auth.client.exception;

/**
 * Exception for Keycloak response mapping errors.
 * Indicates unexpected response structure from Keycloak.
 */
public class KeycloakMappingException extends RuntimeException {
    public KeycloakMappingException(String message) {
        super(message);
    }

    public KeycloakMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}