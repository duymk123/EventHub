package com.eventhub.ticket.dto;

import java.util.List;
import java.util.UUID;

public record CreateTicketTypesRequest(
        UUID eventId,
        List<TicketTypeInput> ticketTypes
) {
}
