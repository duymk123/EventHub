package com.eventhub.notification.common;

import java.time.Instant;

/**
 * Phản hồi lỗi thống nhất, được trả về bởi tất cả các REST service của EventHub.
 */
public record ErrorResponse(String code, String message, Instant timestamp, String path) {}
