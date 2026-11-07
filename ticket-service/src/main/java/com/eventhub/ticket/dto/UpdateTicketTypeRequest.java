package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.enums.TicketTypeStatus;

import java.math.BigDecimal;

public record UpdateTicketTypeRequest(
        BigDecimal price,
        int totalQuota,
        TicketTypeStatus status
) {
}
