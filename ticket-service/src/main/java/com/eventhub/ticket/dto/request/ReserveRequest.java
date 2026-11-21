package com.eventhub.ticket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * expiresAt là hạn Order đề xuất; Ticket không cho vượt TTL 3 phút, thiếu thì tự cấp.
 */
public record ReserveRequest(
        @NotNull UUID orderId,
        @NotNull UUID eventId,
        Instant expiresAt,
        @NotEmpty List<@Valid @NotNull ReserveItem> items) {
}
