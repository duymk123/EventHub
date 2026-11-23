package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.SeatStatus;
import com.eventhub.ticket.common.TicketTypeName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sơ đồ ghế của một zone (C12), GET /api/v1/ticket-types/{id}/seats. */
public record SeatMapResponse(UUID ticketTypeId, TicketTypeName name, BigDecimal price, List<Row> rows,
                              Instant generatedAt) {

    public record Row(int rowIndex, String rowLabel, List<SeatItem> seats) {
    }

    public record SeatItem(UUID id, int seatNumber, String label, SeatStatus status) {
    }
}
