package com.eventhub.analytics.client;

import java.util.UUID;

/**
 * Phần tử phản hồi GET /internal/ticket-types/capacity của Ticket Service.
 *
 * @param eventId UUID sự kiện được yêu cầu.
 * @param capacity Tổng total_quota mọi hạng vé của sự kiện.
 */
public record EventCapacityResponse(UUID eventId, Integer capacity) {}
