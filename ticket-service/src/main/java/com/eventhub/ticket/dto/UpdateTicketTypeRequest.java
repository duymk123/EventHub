package com.eventhub.ticket.dto;

import com.eventhub.ticket.common.TicketTypeStatus;

import java.math.BigDecimal;

public record UpdateTicketTypeRequest(
        BigDecimal price,
        int totalQuota,
        TicketTypeStatus status
) {
}
