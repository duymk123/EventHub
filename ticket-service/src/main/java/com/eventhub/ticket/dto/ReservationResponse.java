package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.Reservation;
import com.eventhub.ticket.entity.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID orderId,
        ReservationStatus status,
        Instant expiresAt,
        List<ReservedItem> items
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getItems().stream()
                        .map(item -> new ReservedItem(
                                item.getTicketType().getId(),
                                item.getTicketType().getName(),
                                item.getQuantity(),
                                item.getUnitPrice()
                        ))
                        .toList()
        );
    }
}
