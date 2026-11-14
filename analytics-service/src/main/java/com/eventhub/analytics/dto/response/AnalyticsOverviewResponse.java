package com.eventhub.analytics.dto.response;

import com.eventhub.analytics.common.Granularity;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi GET /api/v1/analytics/overview (C10).
 *
 * @param from Ngày bắt đầu (bao gồm), yyyy-MM-dd.
 * @param to Ngày kết thúc (bao gồm), yyyy-MM-dd.
 * @param granularity Đơn vị nhóm của series.
 * @param eventId Sự kiện được lọc, null là toàn hệ thống.
 * @param totals Tổng của khoảng from..to.
 * @param previousFrom Đầu khoảng liền trước có cùng số ngày.
 * @param previousTo Cuối khoảng liền trước, bằng from - 1 ngày.
 * @param previousTotals Tổng của khoảng liền trước, dùng tính % thay đổi.
 * @param series Mọi kỳ từ from..to theo thứ tự tăng dần, kỳ không có số liệu trả 0.
 * @param byTicketType Phân bổ theo hạng vé trong from..to.
 */
public record AnalyticsOverviewResponse(
        String from,
        String to,
        Granularity granularity,
        UUID eventId,
        AnalyticsMetrics totals,
        String previousFrom,
        String previousTo,
        AnalyticsMetrics previousTotals,
        List<AnalyticsSeriesPoint> series,
        List<TicketTypeTotals> byTicketType
) {}
