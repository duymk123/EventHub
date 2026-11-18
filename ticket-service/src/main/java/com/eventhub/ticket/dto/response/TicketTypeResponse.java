package com.eventhub.ticket.dto.response;

import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.common.TicketTypeStatus;
import com.eventhub.ticket.entity.TicketType;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketTypeResponse(UUID id, UUID eventId, TicketTypeName name, BigDecimal price, int totalQuota,
                                 int availableQuantity, int reservedQuantity, int soldQuantity,
                                 TicketTypeStatus status) {

    public static TicketTypeResponse from(TicketType type) {
        return new TicketTypeResponse(type.getId(), type.getEventId(), type.getName(), type.getPrice(),
                type.getTotalQuota(), type.getAvailableQuantity(), type.getReservedQuantity(),
                type.getSoldQuantity(), type.getStatus());
    }
}
