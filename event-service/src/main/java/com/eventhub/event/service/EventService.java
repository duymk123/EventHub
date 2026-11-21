package com.eventhub.event.service;

import com.eventhub.event.common.Actor;
import com.eventhub.event.common.EventStatus;
import com.eventhub.event.dto.request.EventRequest;
import com.eventhub.event.dto.request.ReviewRequest;
import com.eventhub.event.dto.response.EventResponse;
import com.eventhub.event.dto.response.EventStatusResponse;
import com.eventhub.event.dto.response.EventSummary;
import com.eventhub.event.dto.response.InternalEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Vòng đời Event (plan Event §2): DRAFT → SUBMITTED → APPROVED/REJECTED → PUBLISHED → CLOSED.
 */
public interface EventService {

    EventResponse create(Actor organizer, EventRequest request);

    /** Chỉ owner, trạng thái DRAFT/REJECTED; sửa REJECTED đưa về DRAFT và xóa lý do từ chối. */
    EventResponse update(Actor organizer, UUID eventId, EventRequest request);

    EventStatusResponse submit(Actor organizer, UUID eventId);

    EventStatusResponse review(Actor admin, UUID eventId, ReviewRequest request);

    /** APPROVED → PUBLISHED sau preflight cấu hình vé ở Ticket (C08). */
    EventStatusResponse publish(Actor organizer, UUID eventId);

    EventStatusResponse close(Actor organizer, UUID eventId);

    Page<EventSummary> searchPublic(String keyword, UUID categoryId, LocalDate startDate, LocalDate endDate,
                                    Pageable pageable);

    EventResponse getPublic(UUID eventId);

    Page<EventResponse> getMine(Actor organizer, EventStatus status, Pageable pageable);

    InternalEventResponse getInternal(UUID eventId);

    List<UUID> getInternalIdsByOrganizer(UUID organizerId);

    List<InternalEventResponse> getInternalBatch(Collection<UUID> eventIds);
}
