package com.eventhub.auth.exception;

/**
 * Standard error codes for Auth Service.
 * Maps to HTTP status codes for consistent API error responses.
 */
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", 400),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", 401),
    UNAUTHORIZED("UNAUTHORIZED", 401),
    FORBIDDEN("FORBIDDEN", 403),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", 403),
    USER_NOT_FOUND("USER_NOT_FOUND", 404),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", 409),
    ACCOUNT_ALREADY_LOCKED("ACCOUNT_ALREADY_LOCKED", 409),
    ACCOUNT_ALREADY_ACTIVE("ACCOUNT_ALREADY_ACTIVE", 409),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", 401),
    REFRESH_TOKEN_EXPIRED("REFRESH_TOKEN_EXPIRED", 401),
    REFRESH_TOKEN_REUSED("REFRESH_TOKEN_REUSED", 401),
    INVALID_ROLE("INVALID_ROLE", 400),
    INVALID_ACTION("INVALID_ACTION", 400),
    IDENTITY_PROVIDER_UNAVAILABLE("IDENTITY_PROVIDER_UNAVAILABLE", 503),
    INTERNAL_ERROR("INTERNAL_ERROR", 500);

    private final String code;
    private final int httpStatus;

    ErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}