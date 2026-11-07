package com.eventhub.ticket.dto;

import java.util.UUID;

public record Holder(
        UUID ticketTypeId,
        String holderName,
        String holderEmail,
        UUID ownerUserId
) {
}
