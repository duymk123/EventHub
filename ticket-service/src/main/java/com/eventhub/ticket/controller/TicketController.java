package com.eventhub.ticket.controller;

import com.eventhub.ticket.dto.*;
import com.eventhub.ticket.entity.TicketScan;
import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.entity.enums.ScanType;
import com.eventhub.ticket.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class TicketController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TicketTypeResponse> createTicketTypes(@RequestBody CreateTicketTypesRequest request) {
        return ticketService.createTicketTypes(
                        request.eventId(),
                        request.ticketTypes().stream()
                                .map(input -> new TicketService.TicketTypeDraft(input.name(), input.price(), input.totalQuota()))
                                .toList())
                .stream().map(TicketTypeResponse::from).toList();
    }

    @PutMapping("/ticket-types/{id}")
    public TicketTypeResponse updateTicketType(
            @PathVariable UUID id,
            @RequestBody UpdateTicketTypeRequest request
    ) {
        return TicketTypeResponse.from(ticketService.updateTicketType(
                id, request.price(), request.totalQuota(), request.status()));
    }

    @GetMapping("/events/{eventId}/ticket-types")
    public List<TicketTypeResponse> listTicketTypes(@PathVariable UUID eventId) {
        return ticketService.listTicketTypes(eventId).stream().map(TicketTypeResponse::from).toList();
    }

    @PostMapping("/internal/reservations")
    public ReservationResponse reserve(@RequestBody ReserveRequest request) {
        Instant expiresAt = request.expiresAt();
        return ReservationResponse.from(ticketService.reserve(
                request.orderId(),
                request.eventId(),
                request.items().stream()
                        .map(item -> new TicketService.ReservationRequestItem(item.ticketTypeId(), item.quantity()))
                        .toList(),
                expiresAt));
    }

    @PostMapping("/internal/reservations/{id}/confirm")
    public ConfirmResponse confirm(@PathVariable UUID id, @RequestBody ConfirmRequest request) {
        return new ConfirmResponse(id, com.eventhub.ticket.entity.enums.ReservationStatus.CONFIRMED,
                ticketService.confirmReservation(id, request.holders()).stream().map(TicketResponse::from).toList());
    }

    @PostMapping("/internal/reservations/{id}/release")
    public ReservationStatusResponse release(@PathVariable UUID id) {
        var reservation = ticketService.releaseReservation(id);
        return new ReservationStatusResponse(reservation.getId(), reservation.getStatus());
    }

    @GetMapping("/tickets/me")
    public List<TicketResponse> getMyTickets(@RequestHeader(USER_ID_HEADER) UUID ownerUserId) {
        return ticketService.getTicketsForUser(ownerUserId).stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/tickets/{id}/qr")
    public QrResponse getQr(
            @RequestHeader(USER_ID_HEADER) UUID ownerUserId,
            @PathVariable UUID id
    ) {
        var ticket = ticketService.getTicketForOwner(id, ownerUserId);
        return new QrResponse(ticket.getId(), ticket.getQrCodeValue());
    }

    @PostMapping("/gate/check-in")
    public GateScanResponse checkIn(@RequestBody GateScanRequest request) {
        return scan(request, ScanType.CHECK_IN);
    }

    @PostMapping("/gate/check-out")
    public GateScanResponse checkOut(@RequestBody GateScanRequest request) {
        return scan(request, ScanType.CHECK_OUT);
    }

    @PostMapping("/internal/tickets/cancel-by-order/{orderId}")
    public CancelResponse cancelByOrder(@PathVariable UUID orderId) {
        return new CancelResponse(orderId, ticketService.cancelByOrder(orderId), "CANCELLED");
    }

    private GateScanResponse scan(GateScanRequest request, ScanType scanType) {
        TicketScan scan = ticketService.scanByQr(
                request.qrCodeValue(), request.eventId(), request.gateId(), scanType);
        return GateScanResponse.from(scan);
    }
}
