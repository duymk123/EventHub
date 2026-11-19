package com.eventhub.event.dto.request;

import com.eventhub.event.common.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EVT-04. reason bắt buộc khi decision = REJECT (kiểm ở service).
 */
public record ReviewRequest(@NotNull ReviewDecision decision, @Size(max = 2000) String reason) {
}
