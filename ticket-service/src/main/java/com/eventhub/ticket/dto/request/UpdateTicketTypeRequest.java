package com.eventhub.ticket.dto.request;

import com.eventhub.ticket.common.TicketTypeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Cập nhật từng phần: trường null giữ nguyên. Tên hạng vé không đổi được.
 */
public record UpdateTicketTypeRequest(
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Min(0) Integer totalQuota,
        TicketTypeStatus status) {
}
