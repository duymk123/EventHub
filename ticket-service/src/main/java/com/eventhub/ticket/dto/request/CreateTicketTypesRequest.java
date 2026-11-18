package com.eventhub.ticket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTicketTypesRequest(
        @NotNull UUID eventId,
        @NotEmpty @Size(max = 3) List<@Valid @NotNull TicketTypeInput> ticketTypes) {
}
