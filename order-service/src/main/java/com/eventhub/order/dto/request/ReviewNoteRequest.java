package com.eventhub.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ghi chú của ADMIN: lý do từ chối hoàn tiền, hoặc cách đã xử lý một khoản đối soát (C21). */
public record ReviewNoteRequest(@NotBlank @Size(max = 500) String reason) {
}
