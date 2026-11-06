package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/sales.
 * revenue/ticketsSold là số liệu gộp, hoàn tiền được ghi tại refundedAt theo ngày Việt Nam,
 * netRevenue = revenue - refundedAmount.
 *
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param from Cận đầu ngày báo cáo, null nghĩa là bỏ cận dưới.
 * @param to Cận cuối ngày báo cáo, null nghĩa là bỏ cận trên.
 * @param totalRevenue Tổng doanh thu gộp trong phạm vi DTO, dashboard dùng toàn sự kiện.
 * @param totalRefunded Tổng tiền hoàn trong khoảng báo cáo.
 * @param netRevenue Doanh thu thuần được tính bằng doanh thu gộp trừ tiền hoàn.
 * @param totalTicketsSold Tổng số vé đã bán gộp trong phạm vi DTO.
 * @param totalTicketsRefunded Tổng số vé hoàn trong khoảng báo cáo.
 * @param daily Danh sách số liệu nhóm theo ngày.
 * @param byTicketType Danh sách số liệu nhóm theo UUID hạng vé.
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
