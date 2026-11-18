package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.TicketTypeName;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Giá snapshot lúc reserve; Order dùng giá này, không nhận giá từ client.
 */
public record ReservedItem(UUID ticketTypeId, TicketTypeName name, int quantity, BigDecimal unitPrice) {
}
