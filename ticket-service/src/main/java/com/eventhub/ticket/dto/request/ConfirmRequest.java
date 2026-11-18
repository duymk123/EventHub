package com.eventhub.ticket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * customerEmail là snapshot email buyer đã xác minh do Order gửi, dùng cho ticket.issued (C02).
 */
public record ConfirmRequest(
        @NotBlank @Email String customerEmail,
        @NotEmpty List<@Valid @NotNull Holder> holders) {
}
