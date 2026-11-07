package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.TicketType;
import com.eventhub.ticket.entity.enums.TicketTypeName;
import com.eventhub.ticket.entity.enums.TicketTypeStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeResponse(
        UUID id,
        UUID eventId,
        TicketTypeName name,
        BigDecimal price,
        int totalQuota,
        int availableQuantity,
        int reservedQuantity,
        int soldQuantity,
        TicketTypeStatus status
) {
    public static TicketTypeResponse from(TicketType ticketType) {
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getEventId(),
                ticketType.getName(),
                ticketType.getPrice(),
                ticketType.getTotalQuota(),
                ticketType.getAvailableQuantity(),
                ticketType.getReservedQuantity(),
                ticketType.getSoldQuantity(),
                ticketType.getStatus()
        );
    }
}
