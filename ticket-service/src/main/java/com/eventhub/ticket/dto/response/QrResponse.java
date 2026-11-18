package com.eventhub.ticket.dto.response;

import java.util.UUID;

public record QrResponse(UUID ticketId, String qrCodeValue) {
}
