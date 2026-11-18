package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.entity.Reservation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(UUID reservationId, UUID orderId, ReservationStatus status, Instant expiresAt,
                                  List<ReservedItem> items) {

    public static ReservationResponse from(Reservation reservation) {
        List<ReservedItem> items = reservation.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getTicketType().getId()))
                .map(item -> new ReservedItem(item.getTicketType().getId(), item.getTicketType().getName(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(), reservation.getStatus(),
                reservation.getExpiresAt(), items);
    }
}
