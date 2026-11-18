package com.eventhub.ticket.dto;

import com.eventhub.ticket.common.ReservationStatus;

import java.util.UUID;

public record ReservationStatusResponse(UUID reservationId, ReservationStatus status) {
}
