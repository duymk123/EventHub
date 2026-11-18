package com.eventhub.ticket.dto;

import java.util.UUID;

public record GateScanRequest(
        String qrCodeValue,
        UUID eventId,
        UUID gateId
) {
}
