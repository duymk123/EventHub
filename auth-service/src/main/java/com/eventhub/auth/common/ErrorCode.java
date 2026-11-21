package com.eventhub.auth.common;

/**
 * Mã lỗi Auth Service trả cho frontend (frontend hiển thị theo code, không theo message).
 * Các code xác thực khớp bảng map trong front-end/src/lib/api/errors.ts.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    CURRENT_PASSWORD_INCORRECT(400),
    UNAUTHORIZED(401),
    INVALID_CREDENTIALS(401),
    INVALID_REFRESH_TOKEN(401),
    REFRESH_TOKEN_EXPIRED(401),
    REFRESH_TOKEN_REUSED(401),
    FORBIDDEN(403),
    ACCOUNT_LOCKED(403),
    USER_NOT_FOUND(404),
    PROFILE_NOT_FOUND(404),
    EMAIL_ALREADY_EXISTS(409),
    PHONE_ALREADY_EXISTS(409),
    PROFILE_ALREADY_EXISTS(409),
    EMAIL_ALREADY_VERIFIED(409),
    ACCOUNT_ALREADY_LOCKED(409),
    ACCOUNT_ALREADY_ACTIVE(409),
    /** ADMIN không tự khóa hoặc tự đổi role của chính mình (tránh mất ADMIN cuối cùng). */
    CANNOT_MODIFY_OWN_ACCOUNT(409),
    /** Sau khi đổi role user vẫn mang hai business role, thường do role mặc định của realm còn chứa CUSTOMER. */
    ROLE_MAPPING_CONFLICT(409),
    INTERNAL_ERROR(500),
    AUTH_PROVIDER_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
