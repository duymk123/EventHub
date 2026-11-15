package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.enums.ReservationStatus;

import java.util.UUID;

public record ReservationStatusResponse(UUID reservationId, ReservationStatus status) {
}
