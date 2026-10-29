package com.eventhub.notification.common;

/**
 * Các mã lỗi chuẩn hóa cho API Notification.
 */
public enum ErrorCode {
    NOTIFICATION_NOT_FOUND(404, "NOTIFICATION_NOT_FOUND"),
    TEMPLATE_NOT_FOUND(404, "TEMPLATE_NOT_FOUND"),
    FORBIDDEN(403, "FORBIDDEN"),
    VALIDATION_ERROR(400, "VALIDATION_ERROR");

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
