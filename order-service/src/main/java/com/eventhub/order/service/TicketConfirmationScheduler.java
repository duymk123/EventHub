package com.eventhub.order.service;

import com.eventhub.order.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Thử lại confirm Ticket cho đơn PAID chưa phát vé (Ticket tạm lỗi lúc IPN/verify). Bỏ qua đơn vừa PAID dưới
 * 10 giây để không chạy song song với lần gọi đầu.
 */
@Component
public class TicketConfirmationScheduler {
    private static final int BATCH_SIZE = 50;
    private static final long GRACE_SECONDS = 10;

    private final OrderRepository orders;
    private final TicketConfirmationService confirmation;
    private final Clock clock;

    public TicketConfirmationScheduler(OrderRepository orders, TicketConfirmationService confirmation, Clock clock) {
        this.orders = orders;
        this.confirmation = confirmation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eventhub.order.confirm-retry-delay-ms:15000}")
    public void retryUnconfirmed() {
        for (UUID orderId : orders.findUnconfirmedPaidIds(clock.instant().minusSeconds(GRACE_SECONDS),
                PageRequest.of(0, BATCH_SIZE))) {
            confirmation.confirmQuietly(orderId);
        }
    }
}
