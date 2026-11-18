package com.eventhub.ticket.dto;

import com.eventhub.ticket.common.TicketTypeName;

import java.math.BigDecimal;

public record TicketTypeInput(
        TicketTypeName name,
        BigDecimal price,
        int totalQuota
) {
}
