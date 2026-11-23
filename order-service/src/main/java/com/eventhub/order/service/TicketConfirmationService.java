package com.eventhub.order.service;

import com.eventhub.order.client.TicketClient;
import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.OrderHolder;
import com.eventhub.order.entity.OrderItem;
import com.eventhub.order.exception.BusinessException;
import com.eventhub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sau PAID: gọi Ticket confirm để phát vé cho đúng ghế, rồi đánh dấu tickets_confirmed_at và ghi outbox order.paid
 * trong cùng transaction (C06, docs/kafka/events.md: order.paid chỉ phát sau khi vé đã phát). Lỗi tạm thời để
 * TicketConfirmationScheduler thử lại; Ticket confirm idempotent nên gọi lặp không tạo vé hai lần.
 */
@Service
public class TicketConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(TicketConfirmationService.class);
    /** Ticket không còn reservation để phát vé: thử lại vô ích. */
    private static final Set<ErrorCode> PERMANENT = Set.of(ErrorCode.RESERVATION_RELEASED_OR_EXPIRED,
            ErrorCode.RESERVATION_NOT_FOUND);

    private record Plan(UUID reservationId, String customerEmail, List<TicketClient.ConfirmHolder> holders) {
    }

    private final OrderRepository orders;
    private final TicketClient ticketClient;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TicketConfirmationService(OrderRepository orders, TicketClient ticketClient, OutboxWriter outbox,
                                     TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.ticketClient = ticketClient;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    /** Không ném lỗi: dùng sau khi IPN/verify đã commit PAID và từ scheduler. */
    public void confirmQuietly(UUID orderId) {
        try {
            confirm(orderId);
        } catch (RuntimeException error) {
            log.warn("Ticket confirm for order {} will be retried: {}", orderId,
                    error instanceof BusinessException business ? business.getErrorCode()
                            : error.getClass().getSimpleName());
        }
    }

    void confirm(UUID orderId) {
        Plan plan = tx.execute(status -> plan(orderId));
        if (plan == null) {
            return;
        }
        try {
            ticketClient.confirm(plan.reservationId(), plan.customerEmail(), plan.holders());
        } catch (BusinessException error) {
            if (!PERMANENT.contains(error.getErrorCode())) {
                throw error;
            }
            tx.executeWithoutResult(status -> markUnissuable(orderId));
            return;
        }
        tx.executeWithoutResult(status -> markConfirmed(orderId));
    }

    private Plan plan(UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAID || order.getTicketsConfirmedAt() != null) {
            return null;
        }
        List<TicketClient.ConfirmHolder> holders = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            for (OrderHolder holder : item.getHolders()) {
                holders.add(new TicketClient.ConfirmHolder(item.getTicketTypeId(), holder.getSeatId(),
                        holder.getHolderName(), holder.getHolderEmail(), order.getCustomerId()));
            }
        }
        return new Plan(order.getReservationId(), order.getCustomerEmail(), holders);
    }

    private void markConfirmed(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PAID || order.getTicketsConfirmedAt() != null) {
            return;
        }
        order.setTicketsConfirmedAt(clock.instant());
        outbox.append(OrderEvents.PAID, order.getId(), order.getEventId(), order.getPaidAt(), OrderEvents.paid(order));
    }

    /**
     * Đã thu tiền nhưng Ticket đã trả ghế (hết hạn giữ): đơn chuyển EXPIRED và báo khách như đơn hết hạn; payment
     * SUCCEEDED + đơn EXPIRED là danh sách ADMIN đối soát hoàn tiền thủ công (hoàn bù tự động là hướng phát triển).
     */
    private void markUnissuable(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PAID || order.getTicketsConfirmedAt() != null) {
            return;
        }
        Instant now = clock.instant();
        order.setStatus(OrderStatus.EXPIRED);
        outbox.append(OrderEvents.FAILED, order.getId(), order.getEventId(), now, OrderEvents.failed(order, "EXPIRED"));
        log.warn("Order {} was paid but its reservation is gone; needs manual reconciliation", orderId);
    }
}
