package com.eventhub.event.controller;

import com.eventhub.event.common.Actor;
import com.eventhub.event.common.EventStatus;
import com.eventhub.event.dto.response.EventResponse;
import com.eventhub.event.service.EventService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** EVT-09: event của ORGANIZER đang đăng nhập, mọi trạng thái; organizerId lấy từ JWT. */
@RestController
@RequestMapping("/api/v1/organizer/events")
public class OrganizerEventController {
    private final EventService eventService;

    public OrganizerEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public Page<EventResponse> getMine(@AuthenticationPrincipal Actor organizer,
                                       @RequestParam(required = false) EventStatus status,
                                       @PageableDefault(size = 20) Pageable pageable) {
        return eventService.getMine(organizer, status, pageable);
    }
}
