package com.eventhub.ticket.dto;

import java.util.UUID;

public record QrResponse(
        UUID ticketId,
        String qrCodeValue
) {
}
