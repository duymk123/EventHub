package com.eventhub.ticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Người nhận một vé. C02: ownerUserId là buyer (customerId tin cậy từ Order); holderEmail null thì vé gửi về
 * email buyer.
 */
public record Holder(
        @NotNull UUID ticketTypeId,
        /** C12: ghế của người này, phải thuộc reservation; mỗi ghế đúng một holder. */
        @NotNull UUID seatId,
        @NotBlank @Size(max = 100) String holderName,
        @Email @Size(max = 255) String holderEmail,
        @NotNull UUID ownerUserId) {
}
