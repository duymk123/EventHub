package com.eventhub.ticket.dto.request;

import com.eventhub.ticket.common.SeatLabels;
import com.eventhub.ticket.common.TicketTypeName;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Một hạng vé = một zone ngồi (C12). Organizer nhập số hàng × số ghế mỗi hàng; totalQuota do server tính.
 * C08: rowCount 0 nghĩa là zone chưa mở bán.
 */
public record TicketTypeInput(
        @NotNull TicketTypeName name,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @NotNull @Min(0) @Max(SeatLabels.MAX_ROWS) Integer rowCount,
        @NotNull @Min(0) @Max(SeatLabels.MAX_SEATS_PER_ROW) Integer seatsPerRow) {
}
