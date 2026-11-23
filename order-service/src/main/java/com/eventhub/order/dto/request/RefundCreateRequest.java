package com.eventhub.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** C04: khách chỉ gửi lý do; số tiền và vé do Order tính (hoàn cả đơn 70%). */
public record RefundCreateRequest(@NotBlank @Size(max = 1000) String reason) {
}
