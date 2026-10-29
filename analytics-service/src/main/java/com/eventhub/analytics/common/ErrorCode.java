package com.eventhub.analytics.common;

/**
 * Các mã lỗi chuẩn hóa cho API Analytics.
 */
public enum ErrorCode {
    INVALID_DATE_RANGE(400, "INVALID_DATE_RANGE"),
    VALIDATION_ERROR(400, "VALIDATION_ERROR"),
    FORBIDDEN_NOT_OWNER(403, "FORBIDDEN_NOT_OWNER"),
    FORBIDDEN(403, "FORBIDDEN"),
    EVENT_NOT_FOUND(404, "EVENT_NOT_FOUND"),
    EVENT_SERVICE_UNAVAILABLE(503, "EVENT_SERVICE_UNAVAILABLE");

    private final int httpStatus;
    private final String code;

    ErrorCode(int httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
