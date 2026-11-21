package com.eventhub.order.service;

import com.eventhub.order.client.TicketClient;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quét đơn quá hạn theo lô, mỗi đơn một transaction. Sau commit mới gọi Ticket release (không giữ lock DB khi
 * gọi HTTP). Release lỗi thì Ticket vẫn tự trả quota vì reservation có cùng hạn với đơn; task RELEASE bền vững
 * (PendingOperation) bổ sung cùng luồng thanh toán.
 */
@Component
public class OrderExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orders;
    private final OrderExpiryService expiryService;
    private final TicketClient ticketClient;
    private final Clock clock;

    public OrderExpiryScheduler(OrderRepository orders, OrderExpiryService expiryService, TicketClient ticketClient,
                                Clock clock) {
        this.orders = orders;
        this.expiryService = expiryService;
        this.ticketClient = ticketClient;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eventhub.order.expiry-delay-ms:10000}")
    public void expireDue() {
        Instant now = clock.instant();
        List<UUID> due = orders.findExpiredIds(OrderStatus.PENDING_PAYMENT, now, PageRequest.of(0, BATCH_SIZE));
        for (UUID orderId : due) {
            try {
                expiryService.expire(orderId, now).ifPresent(ticketClient::releaseQuietly);
            } catch (RuntimeException error) {
                log.warn("Cannot expire order {}: {}", orderId, error.getClass().getSimpleName());
            }
        }
    }
}
