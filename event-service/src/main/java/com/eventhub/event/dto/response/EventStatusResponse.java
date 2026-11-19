package com.eventhub.event.dto.response;

import com.eventhub.event.common.EventStatus;
import com.eventhub.event.entity.Event;

import java.time.Instant;
import java.util.UUID;

public record EventStatusResponse(UUID id, EventStatus status, String rejectReason, Instant updatedAt) {
    public static EventStatusResponse from(Event event) {
        return new EventStatusResponse(event.getId(), event.getStatus(), event.getReviewReason(),
                event.getUpdatedAt());
    }
}
