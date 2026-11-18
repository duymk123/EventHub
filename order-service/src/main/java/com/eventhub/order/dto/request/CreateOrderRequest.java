package com.eventhub.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Client chỉ gửi sự kiện, hạng vé, số lượng và người nhận; giá, tổng tiền và customerId do server quyết định.
 */
public record CreateOrderRequest(
        @NotNull UUID eventId,
        @NotEmpty @Size(max = 3) List<@Valid @NotNull OrderItemInput> items) {

    /** Mỗi vé một holder: số phần tử holders bằng quantity. */
    public record OrderItemInput(
            @NotNull UUID ticketTypeId,
            @NotNull @Min(1) Integer quantity,
            @NotEmpty List<@Valid @NotNull HolderInput> holders) {
    }

    /** C02: holderEmail null thì vé gửi về email đã xác minh của người mua. */
    public record HolderInput(
            @NotBlank @Size(max = 100) String holderName,
            @Email @Size(max = 255) String holderEmail,
            @Size(max = 20) String holderPhone) {
    }
}
