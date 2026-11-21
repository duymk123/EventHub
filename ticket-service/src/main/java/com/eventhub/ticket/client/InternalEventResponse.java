package com.eventhub.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi GET /internal/events/{id} (docs/api/event-service-openapi.yaml, InternalEventResponse).
 * status: DRAFT, SUBMITTED, APPROVED, REJECTED, PUBLISHED, CLOSED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalEventResponse(UUID id, UUID organizerId, String title, String status,
                                    Instant startTime, Instant endTime) {

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }

    /** Trạng thái người mua được xem hạng vé: đang bán hoặc đã đóng bán (vẫn hiển thị). */
    public boolean isPubliclyVisible() {
        return "PUBLISHED".equals(status) || "CLOSED".equals(status);
    }
}
