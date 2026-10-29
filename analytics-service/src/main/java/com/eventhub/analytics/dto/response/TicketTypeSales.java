package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng trong bảng phân tích doanh số theo loại vé.
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
