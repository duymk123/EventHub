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

    /**
     * HTTP status gắn với lỗi nghiệp vụ này.
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Mã lỗi ổn định để frontend nhận biết và xử lý theo nghiệp vụ.
     */
    public String code() {
        return code;
    }
}
