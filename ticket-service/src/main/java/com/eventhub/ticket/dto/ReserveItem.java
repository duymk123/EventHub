package com.eventhub.ticket.dto;

import java.util.UUID;

public record ReserveItem(
        UUID ticketTypeId,
        int quantity
) {
}
