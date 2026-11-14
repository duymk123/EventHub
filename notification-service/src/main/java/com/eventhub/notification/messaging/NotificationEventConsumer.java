package com.eventhub.notification.messaging;

import com.eventhub.notification.messaging.event.*;
import com.eventhub.notification.service.NotificationProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Điểm bắt đầu đọc luồng Kafka của Notification. Container Spring gọi onEvent, EventDecoder kiểm tra record rồi
 * handler tương ứng tiếp tục tạo batch và gửi thông báo.
 */
@Component
public class NotificationEventConsumer {
    private final EventDecoder decoder;
    private final NotificationProcessingService service;

    public NotificationEventConsumer(EventDecoder decoder, NotificationProcessingService service) {
        this.decoder = decoder;
        this.service = service;
    }

    /**
     * Nhận record từ ba topic Notification đang đăng ký và chọn handler theo topic. Container lấy
     * group-id/ack-mode từ application.yml, exception từ handler được chuyển tới KafkaConfig để retry hoặc DLQ.
     */
    @KafkaListener(topics = {"order.failed", "order.refunded", "ticket.issued"})
    public void onEvent(ConsumerRecord<String, String> record) {
        switch (record.topic()) {
            case "order.failed" -> service.handleOrderFailed(decoder.decode(
                    record.value(), OrderFailedEvent.class, record.topic(), record.key()));
            case "order.refunded" -> service.handleOrderRefunded(decoder.decode(
                    record.value(), OrderRefundedEvent.class, record.topic(), record.key()));
            case "ticket.issued" -> service.handleTicketIssued(decoder.decode(
                    record.value(), TicketIssuedEvent.class, record.topic(), record.key()));
            default -> throw new InvalidEventException("Unsupported notification topic");
        }
    }
}
