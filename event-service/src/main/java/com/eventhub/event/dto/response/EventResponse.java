package com.eventhub.event.dto.response;

import com.eventhub.event.common.EventStatus;
import com.eventhub.event.entity.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * Chi tiết event (EVT-01/02/08/09). rejectReason map cột review_reason.
 */
public record EventResponse(UUID id, UUID organizerId, CategoryRef category, String title, String description,
                            Instant startTime, Instant endTime, EventStatus status, String rejectReason,
                            Instant createdAt, Instant updatedAt) {
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getOrganizerId(), CategoryRef.from(event.getCategory()),
                event.getTitle(), event.getDescription(), event.getStartTime(), event.getEndTime(),
                event.getStatus(), event.getReviewReason(), event.getCreatedAt(), event.getUpdatedAt());
    }
}
