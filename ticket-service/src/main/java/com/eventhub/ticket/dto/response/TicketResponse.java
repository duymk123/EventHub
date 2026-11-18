package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.TicketStatus;
import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.entity.Ticket;

import java.time.Instant;
import java.util.UUID;

/**
 * Không chứa QR; QR chỉ trả qua GET /tickets/{id}/qr cho chủ vé.
 */
public record TicketResponse(UUID id, UUID orderId, UUID eventId, UUID ticketTypeId, TicketTypeName ticketTypeName,
                             String holderName, TicketStatus status, Instant issuedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getOrderId(), ticket.getTicketType().getEventId(),
                ticket.getTicketType().getId(), ticket.getTicketType().getName(), ticket.getHolderName(),
                ticket.getStatus(), ticket.getIssuedAt());
    }
}
