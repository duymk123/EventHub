package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của GET /api/v1/analytics/events (C10). Số liệu theo khoảng from..to; title/status/startTime lấy từ
 * Event Service, capacity từ Ticket Service, hai trường lifetime không phụ thuộc khoảng lọc.
 *
 * @param capacity Tổng total_quota các hạng; null khi Ticket Service không phản hồi.
 * @param validTicketsLifetime Vé bán trừ vé hoàn toàn thời gian, mẫu số của tỉ lệ check-in.
 * @param attendeesLifetime Tổng vé đã check-in toàn thời gian.
 */
public record EventAnalyticsRow(
        UUID eventId,
        String title,
        String status,
        Instant startTime,
        Integer capacity,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        long ticketsSold,
        long ticketsRefunded,
        long attendees,
        long validTicketsLifetime,
        long attendeesLifetime
) {}
