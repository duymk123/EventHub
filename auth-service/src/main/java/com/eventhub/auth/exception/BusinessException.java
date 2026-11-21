package com.eventhub.auth.exception;

import com.eventhub.auth.common.ErrorCode;

/**
 * Lỗi nghiệp vụ có mã ổn định; GlobalExceptionHandler đổi thành ErrorResponse đúng HTTP status.
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
