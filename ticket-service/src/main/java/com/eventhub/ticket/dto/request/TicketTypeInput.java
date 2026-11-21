package com.eventhub.ticket.dto.request;

import com.eventhub.ticket.common.TicketTypeName;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Một hạng vé. C08: quota được bằng 0, giá không âm.
 */
public record TicketTypeInput(
        @NotNull TicketTypeName name,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer totalQuota) {
}
