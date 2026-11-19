package com.eventhub.event.common;

import java.time.Instant;

/**
 * Định dạng lỗi thống nhất của EventHub: code, message, timestamp, path.
 */
public record ErrorResponse(String code, String message, Instant timestamp, String path) {
}
