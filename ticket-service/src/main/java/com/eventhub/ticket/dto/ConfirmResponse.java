package com.eventhub.ticket.dto;

import com.eventhub.ticket.common.ReservationStatus;

import java.util.List;
import java.util.UUID;

public record ConfirmResponse(
        UUID reservationId,
        ReservationStatus status,
        List<TicketResponse> tickets
) {
}
