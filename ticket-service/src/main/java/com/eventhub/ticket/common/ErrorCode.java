package com.eventhub.ticket.common;

/**
 * Mã lỗi Ticket Service theo docs/api/ticket-service-openapi.yaml và plan Ticket §6. Client xử lý theo code.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    QUOTA_BELOW_SOLD_RESERVED(400),
    INVALID_QR(400),
    TICKET_WRONG_EVENT(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    FORBIDDEN_NOT_OWNER(403),
    EVENT_NOT_FOUND(404),
    TICKET_TYPE_NOT_FOUND(404),
    RESERVATION_NOT_FOUND(404),
    TICKET_NOT_FOUND(404),
    TICKET_TYPES_ALREADY_EXIST(409),
    INSUFFICIENT_TICKETS(409),
    TICKET_TYPE_NOT_ON_SALE(409),
    /** Cùng orderId nhưng khác sự kiện/hạng vé/số lượng so với reservation đã có. */
    RESERVATION_PAYLOAD_MISMATCH(409),
    RESERVATION_RELEASED_OR_EXPIRED(409),
    RESERVATION_ALREADY_CONFIRMED(409),
    INVALID_TICKET_STATE(409),
    TICKET_ALREADY_CHECKED_IN(409),
    /** Lock/CAS thua do request song song; client thử lại được. */
    CONCURRENT_UPDATE(409),
    INTERNAL_ERROR(500),
    EVENT_SERVICE_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
