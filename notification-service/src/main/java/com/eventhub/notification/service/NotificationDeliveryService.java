package com.eventhub.notification.service;

import com.eventhub.notification.repository.NotificationDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Điều phối giao một EMAIL bằng các transaction ngắn. Claim quyền gửi bằng lease, gọi SMTP ở giữa, rồi ghi kết
 * quả bằng đúng lease token.
 */
@Service
public class NotificationDeliveryService {
    private final NotificationDeliveryRepository repository;
    private final EmailSender sender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final java.util.concurrent.ScheduledExecutorService heartbeat =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "notification-lease-renewal");
                thread.setDaemon(true);
                return thread;
            });

    public NotificationDeliveryService(NotificationDeliveryRepository repository, EmailSender sender,
            PlatformTransactionManager manager, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
    }

    /**
     * Nhận notificationId, claim lease 120 giây trong transaction ngắn rồi gửi SMTP ngoài transaction.
     * Heartbeat gia hạn mỗi 20 giây, kết quả gửi được ghi bằng cùng token để worker cũ chỉ cập nhật khi còn giữ
     * quyền.
     */
    public void deliver(UUID id) {
        // Token mới xác định lượt xử lý này, repository so token khi ghi kết quả gửi.
        UUID token = UUID.randomUUID();
        var now = clock.instant();
        // Transaction thứ nhất chỉ nhận quyền gửi và lấy snapshot rồi kết thúc ngay.
        Optional<NotificationDeliveryRepository.DeliveryWork> claimed = transactions.execute(
                status -> repository.claim(id, token, now, now.plusSeconds(120)));
        if (claimed == null || claimed.isEmpty()) {
            // Có thể là IN_APP, EMAIL đã SENT hoặc target đang có worker khác giữ lease.
            return;
        }
        var work = claimed.get();
        // Job nhỏ gia hạn lease trong lúc gọi SMTP, dùng chung Clock và transaction ngắn.
        var renewal = heartbeat.scheduleAtFixedRate(() -> {
            try {
                transactions.execute(status -> repository.renew(id, token, clock.instant().plusSeconds(120)));
            } catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(NotificationDeliveryService.class)
                        .warn("Notification lease renewal unavailable for {}", id);
            }
        }, 20, 20, java.util.concurrent.TimeUnit.SECONDS);
        try {
            // Lời gọi mạng diễn ra sau khi transaction claim đã kết thúc.
            sender.send(work.recipient(), work.subject(), work.content(), work.payload());
        } catch (RuntimeException error) {
            transactions.executeWithoutResult(status -> repository.markFailed(id, token));
            throw new IllegalStateException("Notification delivery failed");
        } finally {
            // Dừng gia hạn cho target này sau khi lời gọi gửi kết thúc.
            renewal.cancel(false);
        }
        // Transaction thứ hai xác nhận SENT bằng token của lượt xử lý hiện tại.
        // Nếu tiến trình dừng sau SMTP nhưng trước bước này, lần retry có thể gửi email lại.
        Boolean sent = transactions.execute(status -> repository.markSent(id, token, clock.instant()));
        if (!Boolean.TRUE.equals(sent)) {
            throw new IllegalStateException("Notification delivery lease lost");
        }
    }

    /**
     * Được Spring gọi khi tắt application context, dừng thread gia hạn lease để giải phóng tài nguyên.
     */
    @jakarta.annotation.PreDestroy
    public void close() {
        heartbeat.shutdownNow();
    }
}
