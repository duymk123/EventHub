package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Phân bổ theo tên hạng vé trên mọi sự kiện; ticket_type_id khác nhau giữa các sự kiện nên gom theo tên snapshot.
 *
 * @param ticketTypeName VIP, PREMIUM hoặc STANDARD.
 * @param ticketsSold Vé hợp lệ trong kỳ = bán - hoàn.
 * @param netRevenue Doanh thu gộp - tiền hoàn trong kỳ.
 */
public record TicketTypeTotals(String ticketTypeName, long ticketsSold, BigDecimal netRevenue) {}
