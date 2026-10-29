package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Một dòng trong bảng phân tích doanh số theo ngày.
 */
public record DailySales(
        String date,
        BigDecimal revenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        int ticketsSold,
        int ticketsRefunded
) {}
