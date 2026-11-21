package com.eventhub.event.dto.response;

import com.eventhub.event.common.EventStatus;
import com.eventhub.event.entity.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * INT-01/02 cho Ticket và Analytics: mọi trạng thái, đủ để kiểm owner, giờ bắt đầu và trạng thái bán.
 */
public record InternalEventResponse(UUID id, UUID organizerId, String title, EventStatus status,
                                    Instant startTime, Instant endTime) {
    public static InternalEventResponse from(Event event) {
        return new InternalEventResponse(event.getId(), event.getOrganizerId(), event.getTitle(),
                event.getStatus(), event.getStartTime(), event.getEndTime());
    }
}
