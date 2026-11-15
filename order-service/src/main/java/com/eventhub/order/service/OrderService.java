package com.eventhub.order.service;

import com.eventhub.order.entity.Order;
import com.eventhub.order.client.TicketClient;
import com.eventhub.order.dto.CreateOrderRequest;
import com.eventhub.order.entity.OrderHolder;
import com.eventhub.order.entity.OrderItem;
import com.eventhub.order.entity.enums.OrderStatus;
import com.eventhub.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketClient ticketClient;

    public OrderService(OrderRepository orderRepository, TicketClient ticketClient) {
        this.orderRepository = orderRepository;
        this.ticketClient = ticketClient;
    }

    @Transactional
    public Order createOrder(UUID customerId, CreateOrderRequest request) {
        validateRequest(customerId, request);
        UUID orderId = UUID.randomUUID();
        Instant requestedExpiry = Instant.now().plusSeconds(180);
        var reserved = ticketClient.reserve(
                orderId,
                request.eventId(),
                request.items().stream()
                        .map(item -> new TicketClient.ReserveItem(item.ticketTypeId(), item.quantity()))
                        .toList(),
                requestedExpiry);

        try {
            Order order = new Order();
            order.setId(orderId);
            order.setCustomerId(customerId);
            order.setEventId(request.eventId());
            order.setReservationId(reserved.reservationId());
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setExpiresAt(reserved.expiresAt());
            order.setCurrency("VND");

            BigDecimal subtotal = BigDecimal.ZERO;
            for (var input : request.items()) {
                var reservedItem = reserved.items().stream()
                        .filter(item -> item.ticketTypeId().equals(input.ticketTypeId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Ticket service omitted ticket type"));
                BigDecimal lineTotal = reservedItem.unitPrice().multiply(BigDecimal.valueOf(input.quantity()));
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setTicketTypeId(input.ticketTypeId());
                orderItem.setTicketTypeName(reservedItem.name());
                orderItem.setQuantity(input.quantity());
                orderItem.setUnitPrice(reservedItem.unitPrice());
                orderItem.setLineTotal(lineTotal);
                for (var holderInput : input.holders()) {
                    OrderHolder holder = new OrderHolder();
                    holder.setOrderItem(orderItem);
                    holder.setHolderName(holderInput.holderName());
                    holder.setHolderEmail(holderInput.holderEmail());
                    holder.setHolderPhone(holderInput.holderPhone());
                    orderItem.getHolders().add(holder);
                }
                order.getItems().add(orderItem);
                subtotal = subtotal.add(lineTotal);
            }
            order.setSubtotal(subtotal);
            order.setTotalAmount(subtotal);
            return orderRepository.save(order);
        } catch (RuntimeException exception) {
            try {
                ticketClient.release(reserved.reservationId());
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw exception;
        }
    }

    private void validateRequest(UUID customerId, CreateOrderRequest request) {
        if (customerId == null || request == null || request.eventId() == null
                || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("customerId, eventId and items are required");
        }
        Set<UUID> typeIds = new HashSet<>();
        for (var item : request.items()) {
            if (item == null || item.ticketTypeId() == null || item.quantity() < 1
                    || !typeIds.add(item.ticketTypeId())
                    || item.holders() == null || item.holders().size() != item.quantity()) {
                throw new IllegalArgumentException("Each item requires unique type, positive quantity and one holder per ticket");
            }
            for (var holder : item.holders()) {
                if (holder == null || holder.holderName() == null || holder.holderName().isBlank()) {
                    throw new IllegalArgumentException("Holder name is required");
                }
            }
        }
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
