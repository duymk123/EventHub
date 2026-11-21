package com.eventhub.ticket.controller;

import com.eventhub.ticket.dto.request.ConfirmRequest;
import com.eventhub.ticket.dto.request.ReserveRequest;
import com.eventhub.ticket.dto.response.ConfirmResponse;
import com.eventhub.ticket.dto.response.ReservationResponse;
import com.eventhub.ticket.dto.response.ReservationStatusResponse;
import com.eventhub.ticket.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API nội bộ cho Order Service (token service account, không đi qua Gateway public).
 */
@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {
    private final ReservationService reservationService;

    public InternalReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return reservationService.reserve(request);
    }

    @PostMapping("/{id}/confirm")
    public ConfirmResponse confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest request) {
        return reservationService.confirm(id, request);
    }

    @PostMapping("/{id}/release")
    public ReservationStatusResponse release(@PathVariable UUID id) {
        return reservationService.release(id);
    }
}
