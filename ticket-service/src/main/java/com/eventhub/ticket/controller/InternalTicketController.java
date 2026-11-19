package com.eventhub.ticket.controller;

import com.eventhub.ticket.dto.response.CancelResponse;
import com.eventhub.ticket.dto.response.EventCapacityResponse;
import com.eventhub.ticket.dto.response.TicketConfigurationResponse;
import com.eventhub.ticket.service.TicketCancellationService;
import com.eventhub.ticket.service.TicketTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API nội bộ: hủy vé theo đơn (Order), sức chứa theo sự kiện (Analytics, C10) và cấu hình vé trước publish
 * (Event, C08). Quyền từng nhóm khai báo ở SecurityConfig.
 */
@RestController
@RequestMapping("/internal")
public class InternalTicketController {
    private final TicketCancellationService cancellationService;
    private final TicketTypeService ticketTypeService;

    public InternalTicketController(TicketCancellationService cancellationService,
                                    TicketTypeService ticketTypeService) {
        this.cancellationService = cancellationService;
        this.ticketTypeService = ticketTypeService;
    }

    @PostMapping("/tickets/cancel-by-order/{orderId}")
    public CancelResponse cancelByOrder(@PathVariable UUID orderId) {
        return cancellationService.cancelByOrder(orderId);
    }

    /** C08: preflight publish của Event Service. */
    @GetMapping("/events/{eventId}/ticket-types/configuration")
    public TicketConfigurationResponse configuration(@PathVariable UUID eventId) {
        return ticketTypeService.getConfiguration(eventId);
    }

    /** eventIds phân tách dấu phẩy, 1..100 UUID. */
    @GetMapping("/ticket-types/capacity")
    public List<EventCapacityResponse> capacity(@RequestParam List<UUID> eventIds) {
        return ticketTypeService.getCapacityByEvents(eventIds);
    }
}
