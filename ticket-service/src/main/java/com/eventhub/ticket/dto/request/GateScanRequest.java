package com.eventhub.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record GateScanRequest(
        @NotBlank @Size(max = 255) String qrCodeValue,
        @NotNull UUID eventId,
        @NotNull UUID gateId) {
}
