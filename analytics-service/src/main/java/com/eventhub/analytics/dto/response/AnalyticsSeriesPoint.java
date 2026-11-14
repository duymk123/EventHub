package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Một kỳ trong chuỗi số liệu của /analytics/overview. JSON phẳng: period cùng các trường của AnalyticsMetrics.
 *
 * @param period yyyy-MM-dd khi DAY, yyyy-MM khi MONTH.
 */
public record AnalyticsSeriesPoint(
        String period,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        long ticketsSold,
        long ticketsRefunded,
        long attendees
) {
    /**
     * Ghép nhãn kỳ với bộ số liệu đã tổng hợp.
     */
    public static AnalyticsSeriesPoint of(String period, AnalyticsMetrics m) {
        return new AnalyticsSeriesPoint(period, m.grossRevenue(), m.refundedAmount(), m.netRevenue(),
                m.ticketsSold(), m.ticketsRefunded(), m.attendees());
    }
}
