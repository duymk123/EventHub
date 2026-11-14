package com.eventhub.auth.common;

/**
 * Mã lỗi Auth Service trả cho frontend (frontend hiển thị theo code, không theo message).
 * Các code xác thực khớp bảng map trong front-end/src/lib/api/errors.ts.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    EMAIL_ALREADY_EXISTS(409),
    PHONE_ALREADY_EXISTS(409),
    INVALID_CREDENTIALS(401),
    ACCOUNT_LOCKED(403),
    INVALID_REFRESH_TOKEN(401),
    REFRESH_TOKEN_EXPIRED(401),
    REFRESH_TOKEN_REUSED(401),
    USER_NOT_FOUND(404),
    AUTH_PROVIDER_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
