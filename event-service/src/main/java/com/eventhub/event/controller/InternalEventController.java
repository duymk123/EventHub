package com.eventhub.event.controller;

import com.eventhub.event.dto.response.InternalEventResponse;
import com.eventhub.event.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * INT-01/02/03 cho service account (không đi qua Gateway public). Quyền từng caller ở SecurityConfig.
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {
    private final EventService eventService;

    public InternalEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** INT-03: id mọi event của organizer; path literal được Spring ưu tiên hơn /{id}. */
    @GetMapping("/ids")
    public List<UUID> idsByOrganizer(@RequestParam UUID organizerId) {
        return eventService.getInternalIdsByOrganizer(organizerId);
    }

    /** INT-02: 1..100 id phân tách dấu phẩy; id không tồn tại bị bỏ qua. */
    @GetMapping
    public List<InternalEventResponse> batch(@RequestParam List<UUID> ids) {
        return eventService.getInternalBatch(ids);
    }

    /** INT-01: mọi trạng thái, 404 khi không tồn tại. */
    @GetMapping("/{id}")
    public InternalEventResponse get(@PathVariable UUID id) {
        return eventService.getInternal(id);
    }
}
