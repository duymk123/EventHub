package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/sales.
 * Đề xuất C09: revenue/ticketsSold là số liệu gốc (gross), hoàn tiền được ghi tại refundedAt,
 * netRevenue = revenue - refundedAmount.
 */
public record SalesReportResponse(
        UUID eventId,
        String from,
        String to,
        BigDecimal totalRevenue,
        BigDecimal totalRefunded,
        BigDecimal netRevenue,
        int totalTicketsSold,
        int totalTicketsRefunded,
        List<DailySales> daily,
        List<TicketTypeSales> byTicketType
) {}
