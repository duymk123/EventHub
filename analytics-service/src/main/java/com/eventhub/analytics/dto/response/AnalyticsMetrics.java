package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Bộ số liệu chung của thống kê toàn hệ thống (C10).
 *
 * @param grossRevenue Doanh thu gộp, ghi theo ngày Việt Nam của paidAt.
 * @param refundedAmount Tiền hoàn, ghi theo ngày Việt Nam của refundedAt.
 * @param netRevenue Doanh thu gộp trừ tiền hoàn; có thể âm khi kỳ chỉ có hoàn tiền.
 * @param ticketsSold Số vé bán gộp.
 * @param ticketsRefunded Số vé đã hoàn.
 * @param attendees Số vé có lần check-in đầu tiên trong kỳ, mỗi vé tính một lần.
 */
public record AnalyticsMetrics(
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        long ticketsSold,
        long ticketsRefunded,
        long attendees
) {
    public static final AnalyticsMetrics ZERO =
            new AnalyticsMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);

    /**
     * Cộng hai bộ số liệu, dùng khi gộp ngày thành tháng hoặc tính tổng của chuỗi.
     */
    public AnalyticsMetrics plus(AnalyticsMetrics other) {
        return new AnalyticsMetrics(
                grossRevenue.add(other.grossRevenue),
                refundedAmount.add(other.refundedAmount),
                netRevenue.add(other.netRevenue),
                ticketsSold + other.ticketsSold,
                ticketsRefunded + other.ticketsRefunded,
                attendees + other.attendees);
    }
}
