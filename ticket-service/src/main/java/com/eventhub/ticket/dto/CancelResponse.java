package com.eventhub.ticket.dto;

import java.util.UUID;

public record CancelResponse(UUID orderId, int cancelledTickets, String status) {
}
