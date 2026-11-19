package com.eventhub.event.controller;

import com.eventhub.event.common.Actor;
import com.eventhub.event.dto.request.EventRequest;
import com.eventhub.event.dto.response.EventResponse;
import com.eventhub.event.dto.response.EventStatusResponse;
import com.eventhub.event.dto.response.EventSummary;
import com.eventhub.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * EVT-01/02/03/05/06/07/08. GET công khai; ghi chỉ ORGANIZER (SecurityConfig), owner kiểm ở service.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@AuthenticationPrincipal Actor organizer, @Valid @RequestBody EventRequest request) {
        return eventService.create(organizer, request);
    }

    @GetMapping
    public Page<EventSummary> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) UUID category,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                     LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                     LocalDate endDate,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return eventService.searchPublic(keyword, category, startDate, endDate, pageable);
    }

    @GetMapping("/{id}")
    public EventResponse getDetail(@PathVariable UUID id) {
        return eventService.getPublic(id);
    }

    @PutMapping("/{id}")
    public EventResponse update(@AuthenticationPrincipal Actor organizer, @PathVariable UUID id,
                                @Valid @RequestBody EventRequest request) {
        return eventService.update(organizer, id, request);
    }

    @PostMapping("/{id}/submit")
    public EventStatusResponse submit(@AuthenticationPrincipal Actor organizer, @PathVariable UUID id) {
        return eventService.submit(organizer, id);
    }

    @PostMapping("/{id}/publish")
    public EventStatusResponse publish(@AuthenticationPrincipal Actor organizer, @PathVariable UUID id) {
        return eventService.publish(organizer, id);
    }

    @PostMapping("/{id}/close")
    public EventStatusResponse close(@AuthenticationPrincipal Actor organizer, @PathVariable UUID id) {
        return eventService.close(organizer, id);
    }
}
