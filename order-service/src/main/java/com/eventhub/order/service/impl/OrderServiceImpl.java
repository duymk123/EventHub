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
import com.eventhub.order.service.OrderExpiryService;
import com.eventhub.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    /** Trường được phép sắp xếp ở /orders/me. */
    private static final Set<String> SORTABLE = Set.of("createdAt", "id", "totalAmount", "status", "expiresAt");

    /** C12: tối đa 10 ghế mỗi đơn (khớp giới hạn của Ticket). */
    private static final int MAX_SEATS_PER_ORDER = 10;
    private static final int MAX_KEY_LENGTH = 100;

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final TicketClient ticketClient;
    private final OrderExpiryService expiryService;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration paymentTtl;

    public OrderServiceImpl(OrderRepository orders, OrderItemRepository items, TicketClient ticketClient,
                            OrderExpiryService expiryService, TransactionTemplate tx, Clock clock,
                            @Value("${eventhub.order.payment-ttl:PT10M}") Duration paymentTtl) {
        this.orders = orders;
        this.items = items;
        this.ticketClient = ticketClient;
        this.expiryService = expiryService;
        this.tx = tx;
        this.clock = clock;
        this.paymentTtl = paymentTtl;
    }

    @Override
    public CreateResult create(Actor customer, CreateOrderRequest request, String idempotencyKey) {
        if (customer.email() == null || customer.email().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "A verified email is required to place orders");
        }
        validateSeats(request);
        String key = normalizeKey(idempotencyKey);
        String hash = key == null ? null : requestHash(request);
        // C14 trước C20: gửi lại đúng request phải nhận đơn cũ, không phải 409 PENDING_ORDER_EXISTS.
        if (key != null) {
            Optional<CreateResult> replay = replay(customer, key, hash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        // Kiểm trước để không giữ ghế vô ích; uk_order_pending_slot là chốt cuối khi hai request chạy song song.
        ensureNoPendingOrder(customer, request.eventId());

        UUID orderId = UUID.randomUUID();
        TicketClient.Reservation reservation = ticketClient.reserve(orderId, request.eventId(),
                request.items().stream()
                        .map(item -> new TicketClient.ReserveItem(item.ticketTypeId(),
                                item.holders().stream().map(CreateOrderRequest.HolderInput::seatId).toList()))
                        .toList(),
                clock.instant().plus(paymentTtl));
        try {
            Order saved = tx.execute(status -> {
                Order order = buildOrder(orderId, customer, request, reservation);
                order.setIdempotencyKey(key);
                order.setRequestHash(hash);
                return orders.saveAndFlush(order);
            });
            return new CreateResult(OrderResponse.from(saved, saved.getItems()), true);
        } catch (RuntimeException error) {
            // Đã giữ vé nhưng không lưu được đơn: trả vé ngay thay vì đợi hết hạn.
            ticketClient.releaseQuietly(reservation.reservationId());
            if (error instanceof DataIntegrityViolationException) {
                // Thua UNIQUE (customer_id, idempotency_key): request song song cùng key đã tạo đơn.
                if (key != null) {
                    Optional<CreateResult> replay = replay(customer, key, hash);
                    if (replay.isPresent()) {
                        return replay.get();
                    }
                }
                if (orders.findFirstByCustomerIdAndEventIdAndStatus(customer.userId(), request.eventId(),
                        OrderStatus.PENDING_PAYMENT).isPresent()) {
                    throw pendingOrderExists();
                }
            }
            throw error;
        }
    }

    /** C14: cùng key + cùng nội dung trả đơn cũ (200); cùng key khác nội dung → 409 IDEMPOTENCY_KEY_REUSED. */
    private Optional<CreateResult> replay(Actor customer, String key, String hash) {
        return orders.findByCustomerIdAndIdempotencyKey(customer.userId(), key).map(order -> {
            if (!hash.equals(order.getRequestHash())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used for a different order request");
            }
            return new CreateResult(OrderResponse.from(order, items.findByOrderIdIn(List.of(order.getId()))), false);
        });
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * SHA-256 của nội dung chuẩn hóa: sự kiện, các hạng theo ticketTypeId, ghế theo seatId kèm thông tin holder.
     * Thứ tự dòng/holder trong JSON không làm đổi hash.
     */
    static String requestHash(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder(request.eventId().toString());
        request.items().stream()
                .sorted(java.util.Comparator.comparing(CreateOrderRequest.OrderItemInput::ticketTypeId))
                .forEach(item -> {
                    canonical.append('|').append(item.ticketTypeId()).append(':').append(item.quantity());
                    item.holders().stream()
                            .sorted(java.util.Comparator.comparing(CreateOrderRequest.HolderInput::seatId))
                            .forEach(holder -> canonical.append(';').append(holder.seatId())
                                    .append(',').append(holder.holderName().trim())
                                    .append(',').append(Objects.toString(blankToNull(holder.holderEmail()), ""))
                                    .append(',').append(Objects.toString(blankToNull(holder.holderPhone()), "")));
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    /**
     * C20: mỗi khách một đơn PENDING_PAYMENT cho mỗi sự kiện. Đơn cũ đã quá hạn nhưng job chưa quét thì cho hết hạn
     * ngay tại đây để khách không bị chặn oan.
     */
    private void ensureNoPendingOrder(Actor customer, UUID eventId) {
        Order pending = orders.findFirstByCustomerIdAndEventIdAndStatus(customer.userId(), eventId,
                OrderStatus.PENDING_PAYMENT).orElse(null);
        if (pending == null) {
            return;
        }
        Instant now = clock.instant();
        if (pending.getExpiresAt().isAfter(now)) {
            throw pendingOrderExists();
        }
        expiryService.expire(pending.getId(), now).ifPresent(ticketClient::releaseQuietly);
    }

    private static BusinessException pendingOrderExists() {
        return new BusinessException(ErrorCode.PENDING_ORDER_EXISTS,
                "You already have an unpaid order for this event; pay or cancel it first");
    }

    /**
     * C19: khách hủy đơn chưa thanh toán. Khóa đơn để không đua với IPN/expiry; trả ghế sau commit. Payment PENDING
     * giữ nguyên, nếu sau đó VNPay vẫn báo thành công thì payment vào danh sách đối soát (C21).
     */
    @Override
    public OrderResponse cancel(Actor customer, UUID orderId) {
        orders.findByIdAndCustomerId(orderId, customer.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));
        UUID toRelease = tx.execute(status -> {
            Order order = orders.findByIdForUpdate(orderId).orElseThrow();
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return null;
            }
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE, "Order is " + order.getStatus());
            }
            order.setStatus(OrderStatus.CANCELLED);
            return order.getReservationId();
        });
        if (toRelease != null) {
            ticketClient.releaseQuietly(toRelease);
        }
        return getDetail(customer, orderId);
    }

    /** C12: mỗi holder một ghế, holders = quantity, mỗi hạng một dòng, không trùng ghế, tổng ≤ 10 ghế. */
    private static void validateSeats(CreateOrderRequest request) {
        Set<UUID> typeIds = new HashSet<>();
        Set<UUID> seatIds = new HashSet<>();
        for (CreateOrderRequest.OrderItemInput item : request.items()) {
            if (!typeIds.add(item.ticketTypeId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Each ticket type may appear only once");
            }
            if (item.holders().size() != item.quantity()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Each ticket requires exactly one holder (holders.size must equal quantity)");
            }
            for (CreateOrderRequest.HolderInput holder : item.holders()) {
                if (!seatIds.add(holder.seatId())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Each seat may appear only once");
                }
            }
        }
        if (seatIds.size() > MAX_SEATS_PER_ORDER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "An order may contain at most " + MAX_SEATS_PER_ORDER + " seats");
        }
    }

    /** Giá, hạn và nhãn ghế lấy từ phản hồi Ticket, không tin giá client. */
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
        order.setEventStartTime(reservation.eventStartTime());
        order.setCurrency("VND");

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemInput input : request.items()) {
            TicketClient.ReservedItem line = reserved.get(input.ticketTypeId());
            if (line == null || line.quantity() != input.quantity() || line.unitPrice() == null
                    || line.seats() == null) {
                throw new IllegalStateException("Reservation does not match the requested items");
            }
            Map<UUID, String> labels = line.seats().stream().collect(
                    Collectors.toMap(TicketClient.ReservedSeat::seatId, TicketClient.ReservedSeat::seatLabel));
            BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(input.quantity()));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setTicketTypeId(input.ticketTypeId());
            item.setTicketTypeName(line.name());
            item.setQuantity(input.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setLineTotal(lineTotal);
            for (CreateOrderRequest.HolderInput holderInput : input.holders()) {
                String label = labels.get(holderInput.seatId());
                if (label == null) {
                    throw new IllegalStateException("Reservation does not contain the requested seat");
                }
                OrderHolder holder = new OrderHolder();
                holder.setOrderItem(item);
                holder.setSeatId(holderInput.seatId());
                holder.setSeatLabel(label);
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
