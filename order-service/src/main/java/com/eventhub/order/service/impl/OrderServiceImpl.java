package com.eventhub.order.service.impl;

import com.eventhub.order.client.TicketClient;
import com.eventhub.order.common.Actor;
import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.dto.request.CreateOrderRequest;
import com.eventhub.order.dto.response.OrderResponse;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.OrderHolder;
import com.eventhub.order.entity.OrderItem;
import com.eventhub.order.exception.BusinessException;
import com.eventhub.order.repository.OrderItemRepository;
import com.eventhub.order.repository.OrderRepository;
import com.eventhub.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    /** Trường được phép sắp xếp ở /orders/me. */
    private static final Set<String> SORTABLE = Set.of("createdAt", "id", "totalAmount", "status", "expiresAt");

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final TicketClient ticketClient;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration paymentTtl;

    public OrderServiceImpl(OrderRepository orders, OrderItemRepository items, TicketClient ticketClient,
                            TransactionTemplate tx, Clock clock,
                            @Value("${eventhub.order.payment-ttl:PT3M}") Duration paymentTtl) {
        this.orders = orders;
        this.items = items;
        this.ticketClient = ticketClient;
        this.tx = tx;
        this.clock = clock;
        this.paymentTtl = paymentTtl;
    }

    @Override
    public OrderResponse create(Actor customer, CreateOrderRequest request) {
        if (customer.email() == null || customer.email().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "A verified email is required to place orders");
        }
        Set<UUID> typeIds = new HashSet<>();
        for (CreateOrderRequest.OrderItemInput item : request.items()) {
            if (!typeIds.add(item.ticketTypeId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Each ticket type may appear only once");
            }
            if (item.holders().size() != item.quantity()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Each ticket requires exactly one holder (holders.size must equal quantity)");
            }
        }

        UUID orderId = UUID.randomUUID();
        TicketClient.Reservation reservation = ticketClient.reserve(orderId, request.eventId(),
                request.items().stream()
                        .map(item -> new TicketClient.ReserveItem(item.ticketTypeId(), item.quantity()))
                        .toList(),
                clock.instant().plus(paymentTtl));
        try {
            Order saved = tx.execute(status -> orders.save(buildOrder(orderId, customer, request, reservation)));
            return OrderResponse.from(saved, saved.getItems());
        } catch (RuntimeException error) {
            // Đã giữ vé nhưng không lưu được đơn: trả vé ngay thay vì đợi hết hạn.
            ticketClient.releaseQuietly(reservation.reservationId());
            throw error;
        }
    }

    /** Giá và hạn lấy từ phản hồi Ticket, không tin giá client. */
    private static Order buildOrder(UUID orderId, Actor customer, CreateOrderRequest request,
                                    TicketClient.Reservation reservation) {
        Map<UUID, TicketClient.ReservedItem> reserved = reservation.items().stream()
                .collect(Collectors.toMap(TicketClient.ReservedItem::ticketTypeId, Function.identity()));
        Order order = new Order();
        order.setId(orderId);
        order.setCustomerId(customer.userId());
        order.setCustomerEmail(customer.email());
        order.setEventId(request.eventId());
        order.setReservationId(reservation.reservationId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(reservation.expiresAt());
        order.setCurrency("VND");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemInput input : request.items()) {
            TicketClient.ReservedItem line = reserved.get(input.ticketTypeId());
            if (line == null || line.quantity() != input.quantity() || line.unitPrice() == null) {
                throw new IllegalStateException("Reservation does not match the requested items");
            }
            BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(input.quantity()));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setTicketTypeId(input.ticketTypeId());
            item.setTicketTypeName(line.name());
            item.setQuantity(input.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setLineTotal(lineTotal);
            for (CreateOrderRequest.HolderInput holderInput : input.holders()) {
                OrderHolder holder = new OrderHolder();
                holder.setOrderItem(item);
                holder.setHolderName(holderInput.holderName().trim());
                holder.setHolderEmail(blankToNull(holderInput.holderEmail()));
                holder.setHolderPhone(blankToNull(holderInput.holderPhone()));
                item.getHolders().add(holder);
            }
            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal);
        return order;
    }

    @Override
    public Page<OrderResponse> getMine(Actor customer, Pageable pageable) {
        for (Sort.Order sort : pageable.getSort()) {
            if (!SORTABLE.contains(sort.getProperty())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cannot sort by " + sort.getProperty());
            }
        }
        Page<Order> page = orders.findByCustomerId(customer.userId(), pageable);
        Map<UUID, List<OrderItem>> byOrder = items.findByOrderIdIn(page.map(Order::getId).getContent()).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        return page.map(order -> OrderResponse.from(order, byOrder.getOrDefault(order.getId(), List.of())));
    }

    @Override
    public OrderResponse getDetail(Actor customer, UUID orderId) {
        Order order = orders.findByIdAndCustomerId(orderId, customer.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));
        return OrderResponse.from(order, items.findByOrderIdIn(List.of(orderId)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
