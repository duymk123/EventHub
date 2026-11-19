package com.eventhub.event.controller;

import com.eventhub.event.common.Actor;
import com.eventhub.event.dto.request.ReviewRequest;
import com.eventhub.event.dto.response.EventStatusResponse;
import com.eventhub.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** EVT-04, chỉ ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PatchMapping("/{id}/review")
    public EventStatusResponse review(@AuthenticationPrincipal Actor admin, @PathVariable UUID id,
                                      @Valid @RequestBody ReviewRequest request) {
        return eventService.review(admin, id, request);
    }
}
