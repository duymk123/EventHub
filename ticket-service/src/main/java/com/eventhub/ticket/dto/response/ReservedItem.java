package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.TicketTypeName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Giá snapshot lúc giữ ghế; Order dùng giá này, không nhận giá từ client. seats là các ghế đã giữ của zone (C12).
 */
public record ReservedItem(UUID ticketTypeId, TicketTypeName name, int quantity, BigDecimal unitPrice,
                           List<ReservedSeat> seats) {

    public record ReservedSeat(UUID seatId, String seatLabel) {
    }
}
