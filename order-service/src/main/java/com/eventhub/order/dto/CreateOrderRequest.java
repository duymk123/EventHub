package com.eventhub.order.dto;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(UUID eventId, List<OrderItemInput> items) {
    public record OrderItemInput(UUID ticketTypeId, int quantity, List<HolderInput> holders) {
    }

    public record HolderInput(String holderName, String holderEmail, String holderPhone) {
    }
}
