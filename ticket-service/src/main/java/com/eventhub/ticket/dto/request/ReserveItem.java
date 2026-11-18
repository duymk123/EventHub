package com.eventhub.ticket.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveItem(@NotNull UUID ticketTypeId, @NotNull @Min(1) Integer quantity) {
}
