package com.eventhub.ticket.dto.response;

import java.util.UUID;

/**
 * cancelledTickets là số vé hủy ở lần gọi này; gọi lặp trả 0 và không hoàn quota lần hai.
 */
public record CancelResponse(UUID orderId, int cancelledTickets, String status) {
}
