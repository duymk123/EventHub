package com.eventhub.auth.exception;

import com.eventhub.auth.client.exception.KeycloakAuthenticationException;
import com.eventhub.auth.client.exception.KeycloakMappingException;
import com.eventhub.auth.client.exception.KeycloakUnavailableException;
import com.eventhub.auth.dto.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for Auth Service.
 * Maps exceptions to standardized ErrorResponse format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle validation errors (e.g., @Valid failures)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI());
    }

    // Handle WebClient errors (e.g., Keycloak API errors)
    @ExceptionHandler(KeycloakAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakAuthError(KeycloakAuthenticationException ex, HttpServletRequest request) {
        ErrorCode errorCode;
        try {
            errorCode = ErrorCode.valueOf(ex.getErrorCode());
        } catch (IllegalArgumentException e) {
            errorCode = ErrorCode.INVALID_CREDENTIALS;
        }
        return buildErrorResponse(errorCode, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(KeycloakUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakUnavailable(KeycloakUnavailableException ex, HttpServletRequest request) {
        log.error("Keycloak unavailable: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, "Identity provider temporarily unavailable", request.getRequestURI());
    }

    @ExceptionHandler(KeycloakMappingException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakMappingError(KeycloakMappingException ex, HttpServletRequest request) {
        log.error("Keycloak mapping error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, "Identity provider response mapping failed", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildErrorResponse(ErrorCode.VALIDATION_ERROR, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_ERROR, "Internal server error", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(ErrorCode errorCode, String message, String path) {
        ErrorResponse response = ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }
}