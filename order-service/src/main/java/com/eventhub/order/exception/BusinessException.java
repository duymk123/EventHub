package com.eventhub.order.exception;

import com.eventhub.order.common.ErrorCode;

import java.util.List;
import java.util.UUID;

/**
 * Lỗi nghiệp vụ có mã ổn định; GlobalExceptionHandler đổi thành ErrorResponse đúng HTTP status.
 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    /** Chỉ dùng cho SEAT_TAKEN (C12). */
    private final List<UUID> takenSeatIds;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<UUID> takenSeatIds) {
        super(message);
        this.errorCode = errorCode;
        this.takenSeatIds = takenSeatIds == null ? null : List.copyOf(takenSeatIds);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<UUID> getTakenSeatIds() {
        return takenSeatIds;
    }
}
