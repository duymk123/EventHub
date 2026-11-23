package com.eventhub.ticket.dto.request;

import com.eventhub.ticket.common.SeatLabels;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** C12: các ghế khách chọn trong một zone; tổng mọi item tối đa 10 ghế. */
public record ReserveItem(
        @NotNull UUID ticketTypeId,
        @NotEmpty @Size(max = SeatLabels.MAX_SEATS_PER_ORDER) List<@NotNull UUID> seatIds) {
}
