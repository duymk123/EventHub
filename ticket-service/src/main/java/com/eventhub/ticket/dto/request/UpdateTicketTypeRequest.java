package com.eventhub.ticket.dto.request;

import com.eventhub.ticket.common.SeatLabels;
import com.eventhub.ticket.common.TicketTypeStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Cập nhật từng phần: trường null giữ nguyên. Tên hạng vé không đổi được. Sơ đồ ghế (C12): thêm hàng luôn được;
 * bớt hàng hoặc đổi số ghế mỗi hàng chỉ khi các ghế bị ảnh hưởng chưa từng được giữ/bán.
 */
public record UpdateTicketTypeRequest(
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Min(0) @Max(SeatLabels.MAX_ROWS) Integer rowCount,
        @Min(0) @Max(SeatLabels.MAX_SEATS_PER_ROW) Integer seatsPerRow,
        TicketTypeStatus status) {
}
