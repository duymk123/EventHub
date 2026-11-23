package com.eventhub.ticket.exception;

import com.eventhub.ticket.common.ErrorCode;

import java.util.List;
import java.util.UUID;

/**
 * Lỗi nghiệp vụ có mã ổn định; GlobalExceptionHandler đổi thành ErrorResponse đúng HTTP status.
 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final List<UUID> takenSeatIds;

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<UUID> takenSeatIds) {
        super(message);
        this.errorCode = errorCode;
        this.takenSeatIds = takenSeatIds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Chỉ có giá trị với SEAT_TAKEN. */
    public List<UUID> getTakenSeatIds() {
        return takenSeatIds;
    }
}
