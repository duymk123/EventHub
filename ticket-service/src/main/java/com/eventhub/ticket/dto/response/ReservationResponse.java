package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.ReservationStatus;
import com.eventhub.ticket.entity.Reservation;
import com.eventhub.ticket.entity.ReservationSeat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(UUID reservationId, UUID orderId, ReservationStatus status, Instant expiresAt,
                                  Instant eventStartTime, List<ReservedItem> items) {

    public static ReservationResponse from(Reservation reservation, List<ReservationSeat> seats) {
        List<ReservedItem> items = reservation.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getTicketType().getId()))
                .map(item -> new ReservedItem(item.getTicketType().getId(), item.getTicketType().getName(),
                        item.getQuantity(), item.getUnitPrice(),
                        seats.stream()
                                .filter(seat -> seat.getSeat().getTicketType().getId()
                                        .equals(item.getTicketType().getId()))
                                .map(seat -> new ReservedItem.ReservedSeat(seat.getSeat().getId(), seat.getSeatLabel()))
                                .toList()))
                .toList();
        return new ReservationResponse(reservation.getId(), reservation.getOrderId(), reservation.getStatus(),
                reservation.getExpiresAt(), reservation.getEventStartTime(), items);
    }
}
