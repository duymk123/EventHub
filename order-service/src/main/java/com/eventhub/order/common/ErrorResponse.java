package com.eventhub.order.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Định dạng lỗi thống nhất của EventHub: code, message, timestamp, path. takenSeatIds chỉ có khi code = SEAT_TAKEN
 * (C12) để frontend tô xám các ghế đã mất và cho chọn lại.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Instant timestamp, String path, List<UUID> takenSeatIds) {

    public ErrorResponse(String code, String message, Instant timestamp, String path) {
        this(code, message, timestamp, path, null);
    }
}
