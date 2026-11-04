package com.eventhub.order.dto;

import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.OrderHolder;
import com.eventhub.order.entity.OrderItem;
import com.eventhub.order.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID eventId,
        UUID reservationId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        String currency,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getEventId(),
                order.getReservationId(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getSubtotal(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCreatedAt()
        );
    }

    public record OrderItemResponse(
            UUID ticketTypeId,
            String ticketTypeName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            List<HolderResponse> holders
    ) {
        private static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                    item.getTicketTypeId(),
                    item.getTicketTypeName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal(),
                    item.getHolders().stream().map(HolderResponse::from).toList()
            );
        }
    }

    public record HolderResponse(
            String holderName,
            String holderEmail,
            String holderPhone
    ) {
        private static HolderResponse from(OrderHolder holder) {
            return new HolderResponse(
                    holder.getHolderName(),
                    holder.getHolderEmail(),
                    holder.getHolderPhone()
            );
        }
    }
}
