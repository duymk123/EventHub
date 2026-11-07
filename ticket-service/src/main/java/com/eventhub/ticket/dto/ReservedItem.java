package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.enums.TicketTypeName;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservedItem(
        UUID ticketTypeId,
        TicketTypeName name,
        int quantity,
        BigDecimal unitPrice
) {
}
