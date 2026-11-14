package com.eventhub.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;

/**
 * Nhận tín hiệu thay đổi sau commit và push snapshot tới destination STOMP của event. synchronized tuần tự hóa
 * thao tác đọc/gửi trong Analytics instance hiện tại.
 */
@Service
public class DashboardPushService {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardPushService.class);
    private final DashboardReadService reader;
    private final SimpMessagingTemplate messaging;

    public DashboardPushService(DashboardReadService reader, SimpMessagingTemplate messaging) {
        this.reader = reader;
        this.messaging = messaging;
    }

    /**
     * Callback AFTER_COMMIT đọc snapshot mới rồi gửi tới /topic/event/{eventId}/dashboard bằng
     * SimpMessagingTemplate. Lỗi push được log an toàn để projection đã commit vẫn được giữ.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onChanged(DashboardChangedEvent event) {
        try {
            // Đọc ở bean có REQUIRES_NEW để lấy snapshot mới sau commit, rồi gửi qua STOMP.
            messaging.convertAndSend("/topic/event/" + event.eventId() + "/dashboard", reader.getForPush(event.eventId()));
        } catch (RuntimeException error) {
            LOG.warn("Dashboard push unavailable for event {}", event.eventId());
        }
    }
}
