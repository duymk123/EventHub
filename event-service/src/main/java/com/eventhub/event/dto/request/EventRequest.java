package com.eventhub.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Tạo/sửa event (EVT-01/02). startTime phải ở tương lai và trước endTime, kiểm ở service.
 */
public record EventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 20000) String description,
        @NotNull UUID categoryId,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}
