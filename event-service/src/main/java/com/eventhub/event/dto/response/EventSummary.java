package com.eventhub.event.dto.response;

import com.eventhub.event.entity.Event;

import java.time.Instant;
import java.util.UUID;

/** Item tìm kiếm public (EVT-07), không có giá vé. */
public record EventSummary(UUID id, String title, CategoryRef category, Instant startTime, Instant endTime) {
    public static EventSummary from(Event event) {
        return new EventSummary(event.getId(), event.getTitle(), CategoryRef.from(event.getCategory()),
                event.getStartTime(), event.getEndTime());
    }
}
