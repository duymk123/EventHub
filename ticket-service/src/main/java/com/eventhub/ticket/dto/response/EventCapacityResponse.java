package com.eventhub.ticket.dto.response;

import java.util.UUID;

/**
 * C10: capacity = SUM(total_quota) mọi hạng của sự kiện (kể cả INACTIVE); chưa có hạng vé thì 0.
 */
public record EventCapacityResponse(UUID eventId, long capacity) {
}
