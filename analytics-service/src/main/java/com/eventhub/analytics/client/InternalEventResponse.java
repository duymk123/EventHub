package com.eventhub.analytics.client;

import java.util.UUID;

/**
 * Dữ liệu sự kiện nội bộ tối thiểu, trả về bởi Event Service.
 */
public record InternalEventResponse(
        UUID id,
        UUID organizerId,
        String title,
        String status,
        java.time.Instant startTime,
        java.time.Instant endTime
) {}
