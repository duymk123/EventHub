package com.eventhub.event.common;

/**
 * Mã lỗi Event Service theo docs/api/event-service-openapi.yaml và plan Event §6. Client xử lý theo code.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400),
    INVALID_DATE_RANGE(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    FORBIDDEN_NOT_OWNER(403),
    EVENT_NOT_FOUND(404),
    CATEGORY_NOT_FOUND(404),
    INVALID_EVENT_STATE(409),
    CATEGORY_NAME_EXISTS(409),
    CATEGORY_IN_USE(409),
    TICKET_CONFIGURATION_NOT_READY(409),
    /** Lock/CAS thua do request song song; client thử lại được. */
    CONCURRENT_UPDATE(409),
    INTERNAL_ERROR(500),
    TICKET_SERVICE_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
