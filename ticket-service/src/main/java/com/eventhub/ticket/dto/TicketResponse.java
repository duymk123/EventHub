package com.eventhub.ticket.dto;

import com.eventhub.ticket.entity.Ticket;
import com.eventhub.ticket.entity.enums.TicketStatus;
import com.eventhub.ticket.entity.enums.TicketTypeName;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID orderId,
        UUID eventId,
        UUID ticketTypeId,
        TicketTypeName ticketTypeName,
        String holderName,
        TicketStatus status,
        Instant issuedAt
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getTicketType().getEventId(),
                ticket.getTicketType().getId(),
                ticket.getTicketType().getName(),
                ticket.getHolderName(),
                ticket.getStatus(),
                ticket.getIssuedAt()
        );
    }
}
