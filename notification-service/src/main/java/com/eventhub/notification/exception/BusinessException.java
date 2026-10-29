package com.eventhub.notification.exception;

import com.eventhub.notification.common.ErrorCode;

/**
 * Được ném khi vi phạm quy tắc kinh doanh.
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
