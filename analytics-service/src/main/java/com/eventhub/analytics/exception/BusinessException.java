package com.eventhub.analytics.exception;

import com.eventhub.analytics.common.ErrorCode;

/**
 * Được ném khi vi phạm quy tắc kinh doanh (quyền sở hữu, khoảng ngày, trạng thái).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
