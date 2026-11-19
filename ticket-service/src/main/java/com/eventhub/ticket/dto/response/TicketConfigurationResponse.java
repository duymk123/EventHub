package com.eventhub.ticket.dto.response;

import java.util.UUID;

/**
 * Preflight publish C08 cho Event Service: sự kiện đủ điều kiện bán khi có ít nhất một hạng ACTIVE với
 * total_quota &gt; 0. Không buộc đủ ba hạng.
 *
 * @param activeTypeCount số hạng ACTIVE có total_quota &gt; 0.
 * @param activeCapacity tổng total_quota của các hạng ACTIVE.
 */
public record TicketConfigurationResponse(UUID eventId, int activeTypeCount, long activeCapacity,
                                          boolean readyToPublish) {
}
