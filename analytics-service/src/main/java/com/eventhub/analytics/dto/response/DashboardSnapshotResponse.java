package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/dashboard và được đẩy qua WebSocket.
 * Đề xuất C09: totalRevenue/totalTicketsSold là số liệu sau hoàn tiền (net), khác với báo cáo doanh số (gốc).
 */
public record DashboardSnapshotResponse(
        UUID eventId,
        BigDecimal totalRevenue,
        int totalTicketsSold,
        int currentAttendance,
        int totalCheckedIn,
        Instant updatedAt
) {}
