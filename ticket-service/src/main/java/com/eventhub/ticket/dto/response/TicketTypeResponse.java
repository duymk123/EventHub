package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.SeatStatus;
import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.common.TicketTypeStatus;
import com.eventhub.ticket.entity.TicketType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Một zone (C12): cấu hình hàng × ghế và số ghế theo trạng thái, đếm từ bảng seats. */
public record TicketTypeResponse(UUID id, UUID eventId, TicketTypeName name, BigDecimal price, int rowCount,
                                 int seatsPerRow, int totalQuota, long availableSeats, long heldSeats,
                                 long soldSeats, TicketTypeStatus status) {

    public static TicketTypeResponse from(TicketType type, Map<SeatStatus, Long> counts) {
        long held = counts.getOrDefault(SeatStatus.HELD, 0L);
        long sold = counts.getOrDefault(SeatStatus.SOLD, 0L);
        long available = counts.getOrDefault(SeatStatus.AVAILABLE, 0L);
        return new TicketTypeResponse(type.getId(), type.getEventId(), type.getName(), type.getPrice(),
                type.getRowCount(), type.getSeatsPerRow(), type.getTotalQuota(), available, held, sold,
                type.getStatus());
    }
}
