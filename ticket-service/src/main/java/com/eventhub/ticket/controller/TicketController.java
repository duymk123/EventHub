package com.eventhub.ticket.controller;

import com.eventhub.ticket.common.Actor;
import com.eventhub.ticket.dto.response.QrResponse;
import com.eventhub.ticket.dto.response.TicketResponse;
import com.eventhub.ticket.service.TicketQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Vé của CUSTOMER đang đăng nhập; userId lấy từ JWT, không từ header.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketQueryService ticketQueryService;

    public TicketController(TicketQueryService ticketQueryService) {
        this.ticketQueryService = ticketQueryService;
    }

    @GetMapping("/me")
    public List<TicketResponse> getMine(@AuthenticationPrincipal Actor actor) {
        return ticketQueryService.getMine(actor);
    }

    @GetMapping("/{id}/qr")
    public QrResponse getQr(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return ticketQueryService.getQr(actor, id);
    }
}
