package com.eventhub.ticket.controller;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.dto.request.CreateTicketTypesRequest;
import com.eventhub.ticket.dto.request.UpdateTicketTypeRequest;
import com.eventhub.ticket.dto.response.SeatMapResponse;
import com.eventhub.ticket.dto.response.TicketTypeResponse;
import com.eventhub.ticket.service.TicketTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Hạng vé: POST/PUT cho ORGANIZER chủ sự kiện, GET công khai (Gateway giữ nguyên prefix /api/v1).
 */
@RestController
@RequestMapping("/api/v1")
public class TicketTypeController {
    private final TicketTypeService ticketTypeService;

    public TicketTypeController(TicketTypeService ticketTypeService) {
        this.ticketTypeService = ticketTypeService;
    }

    @PostMapping("/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TicketTypeResponse> create(@AuthenticationPrincipal Actor actor,
                                           @Valid @RequestBody CreateTicketTypesRequest request) {
        return ticketTypeService.createTypes(actor, request);
    }

    @PutMapping("/ticket-types/{id}")
    public TicketTypeResponse update(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateTicketTypeRequest request) {
        return ticketTypeService.updateType(actor, id, request);
    }

    /** C12: sơ đồ ghế của một zone, frontend gọi lại mỗi 3–5 giây. */
    @GetMapping("/ticket-types/{id}/seats")
    public SeatMapResponse seatMap(@PathVariable UUID id) {
        return ticketTypeService.getSeatMap(id);
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> listByEvent(@AuthenticationPrincipal Actor actor, @PathVariable UUID eventId) {
        return ticketTypeService.listTypes(eventId, actor);
    }
}
