package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng trong bảng phân tích doanh số theo loại vé.
 *
 * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
 * @param ticketTypeName Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
 * @param revenue Doanh thu gộp của dòng báo cáo.
 * @param refundedAmount Tiền hoàn được phân bổ cho hạng vé hoặc tổng tiền hoàn của dòng báo cáo.
 * @param netRevenue Doanh thu thuần được tính bằng doanh thu gộp trừ tiền hoàn.
 * @param ticketsSold Số vé đã bán gộp của dòng.
 * @param ticketsRefunded Số vé đã hoàn của dòng.
 */
public record TicketTypeSales(
        UUID ticketTypeId,
        String ticketTypeName,
        BigDecimal revenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        int ticketsSold,
        int ticketsRefunded
) {}
