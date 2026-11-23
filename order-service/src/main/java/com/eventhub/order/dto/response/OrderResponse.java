package com.eventhub.order.dto.response;

import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.OrderHolder;
import com.eventhub.order.entity.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot tên/giá tại thời điểm đặt, không tính lại theo giá hiện tại của Ticket.
 */
public record OrderResponse(UUID id, UUID customerId, UUID eventId, UUID reservationId, OrderStatus status,
                            List<OrderItemResponse> items, BigDecimal subtotal, BigDecimal totalAmount,
                            String currency, Instant expiresAt, Instant paidAt, Instant createdAt) {

    /** items truyền riêng để trang lịch sử nạp item/holder bằng một truy vấn gộp, không N+1. */
    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getEventId(), order.getReservationId(),
                order.getStatus(),
                items.stream().sorted(Comparator.comparing(OrderItem::getTicketTypeId))
                        .map(OrderItemResponse::from).toList(),
                order.getSubtotal(), order.getTotalAmount(), order.getCurrency(), order.getExpiresAt(),
                order.getPaidAt(), order.getCreatedAt());
    }

    public record OrderItemResponse(UUID ticketTypeId, String ticketTypeName, Integer quantity, BigDecimal unitPrice,
                                    BigDecimal lineTotal, List<HolderResponse> holders) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getTicketTypeId(), item.getTicketTypeName(), item.getQuantity(),
                    item.getUnitPrice(), item.getLineTotal(),
                    item.getHolders().stream().sorted(Comparator.comparing(OrderHolder::getSeatLabel))
                            .map(HolderResponse::from).toList());
        }
    }

    /** C12: mỗi holder gắn một ghế; seatLabel là snapshot lúc giữ ghế. */
    public record HolderResponse(UUID id, UUID seatId, String seatLabel, String holderName, String holderEmail,
                                 String holderPhone) {

        static HolderResponse from(OrderHolder holder) {
            return new HolderResponse(holder.getId(), holder.getSeatId(), holder.getSeatLabel(),
                    holder.getHolderName(), holder.getHolderEmail(), holder.getHolderPhone());
        }
    }
}
