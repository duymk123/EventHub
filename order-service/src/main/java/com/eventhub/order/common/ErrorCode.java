package com.eventhub.order.common;

/**
 * Mã lỗi Order Service (docs/api/order-service-openapi.yaml, plan Order). Lỗi nghiệp vụ của Ticket khi reserve
 * được giữ nguyên code để client hiển thị đúng (INSUFFICIENT_TICKETS, TICKET_TYPE_NOT_ON_SALE…).
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    /** Tài khoản chưa có email đã xác minh nên không snapshot được email người mua (C02). */
    EMAIL_NOT_VERIFIED(403),
    ORDER_NOT_FOUND(404),
    EVENT_NOT_FOUND(404),
    TICKET_TYPE_NOT_FOUND(404),
    INSUFFICIENT_TICKETS(409),
    TICKET_TYPE_NOT_ON_SALE(409),
    CONCURRENT_UPDATE(409),
    INTERNAL_ERROR(500),
    TICKET_SERVICE_UNAVAILABLE(503),
    EVENT_SERVICE_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
