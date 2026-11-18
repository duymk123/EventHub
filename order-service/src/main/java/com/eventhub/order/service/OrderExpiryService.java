package com.eventhub.order.service;

import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.entity.Order;
import com.eventhub.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PENDING_PAYMENT quá hạn → EXPIRED cùng outbox order.failed reason=EXPIRED trong một transaction (plan Order §2).
 * Không chạm PAID/FAILED/REFUNDED; chỉ bên thắng chuyển trạng thái mới phát event.
 */
@Service
public class OrderExpiryService {
    private final OrderRepository orders;
    private final OutboxWriter outbox;

    public OrderExpiryService(OrderRepository orders, OutboxWriter outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * @return reservationId cần release nếu đơn vừa hết hạn; rỗng nếu đơn đã đổi trạng thái hoặc chưa tới hạn.
     */
    @Transactional
    public Optional<UUID> expire(UUID orderId, Instant now) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        order.setStatus(OrderStatus.EXPIRED);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("orderId", order.getId());
        fields.put("customerId", order.getCustomerId());
        fields.put("customerEmail", order.getCustomerEmail());
        fields.put("reason", "EXPIRED");
        fields.put("totalAmount", order.getTotalAmount());
        outbox.append("order.failed", order.getId(), order.getEventId(), now, fields);
        return Optional.of(order.getReservationId());
    }
}
