package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Một dòng trong bảng phân tích doanh số theo ngày.
 *
 * @param date Ngày báo cáo Việt Nam theo định dạng yyyy-MM-dd.
 * @param revenue Doanh thu gộp của dòng báo cáo.
 * @param refundedAmount Tiền hoàn được phân bổ cho hạng vé hoặc tổng tiền hoàn của dòng báo cáo.
 * @param netRevenue Doanh thu thuần được tính bằng doanh thu gộp trừ tiền hoàn.
 * @param ticketsSold Số vé đã bán gộp của dòng.
 * @param ticketsRefunded Số vé đã hoàn của dòng.
 */
public record DailySales(
        String date,
        BigDecimal revenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue,
        int ticketsSold,
        int ticketsRefunded
) {}
