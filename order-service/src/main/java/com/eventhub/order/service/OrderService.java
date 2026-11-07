package com.eventhub.order.service;

import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.enums.OrderStatus;
import com.eventhub.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Page<Order> getCustomerOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Order getCustomerOrder(UUID customerId, UUID orderId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional
    public Order createPendingOrder(Order order) {
        if (order == null || order.getCustomerId() == null || order.getReservationId() == null) {
            throw new IllegalArgumentException("customerId and reservationId are required");
        }
        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.PENDING_PAYMENT);
        }
        if (order.getSubtotal() == null || order.getTotalAmount() == null
                || order.getSubtotal().signum() < 0 || order.getTotalAmount().signum() < 0) {
            throw new IllegalArgumentException("Order amounts must be non-negative");
        }
        if (order.getExpiresAt() == null || !order.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Order expiration must be in the future");
        }
        return orderRepository.save(order);
    }

    @Transactional
    public Order markPaid(UUID customerId, UUID orderId) {
        Order order = getCustomerOrder(customerId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending orders can be paid");
        }
        if (!order.getExpiresAt().isAfter(Instant.now())) {
            order.setStatus(OrderStatus.EXPIRED);
            throw new IllegalStateException("Order has expired");
        }
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        return order;
    }

    @Transactional
    public int expirePendingOrders(Instant now) {
        return orderRepository.expirePendingOrders(now, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
    }

    public static final class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(UUID orderId) {
            super("Order not found: " + orderId);
        }
    }
}
