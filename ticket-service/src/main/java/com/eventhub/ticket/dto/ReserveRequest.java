package com.eventhub.ticket.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveRequest(
        UUID orderId,
        UUID eventId,
        Instant expiresAt,
        List<ReserveItem> items
) {
}
