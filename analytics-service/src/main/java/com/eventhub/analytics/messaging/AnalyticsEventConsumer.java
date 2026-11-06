package com.eventhub.analytics.messaging;

import com.eventhub.analytics.messaging.event.*;
import com.eventhub.analytics.service.AnalyticsProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Điểm bắt đầu đọc luồng Kafka của Analytics. Chọn record theo topic, gọi projection rồi chỉ bỏ qua
 * DuplicateMessageException sau khi transaction claim trùng đã rollback.
 */
@Component
public class AnalyticsEventConsumer {
    private final EventDecoder decoder;
    private final AnalyticsProjectionService service;

    public AnalyticsEventConsumer(EventDecoder decoder, AnalyticsProjectionService service) {
        this.decoder = decoder;
        this.service = service;
    }

    /**
     * Giải mã record theo một trong bốn topic rồi gọi projection. DuplicateMessageException được bắt sau khi
     * Spring hoàn tất rollback transaction của service, các lỗi còn lại đi qua error handler của container.
     */
    @KafkaListener(topics = {"order.paid", "order.refunded", "ticket.checked-in", "ticket.checked-out"})
    public void onEvent(ConsumerRecord<String, String> record) {
        try {
            switch (record.topic()) {
                case "order.paid" -> service.handlePaid(decoder.decode(
                        record.value(), OrderPaidEvent.class, record.topic(), record.key()));
                case "order.refunded" -> service.handleRefunded(decoder.decode(
                        record.value(), OrderRefundedEvent.class, record.topic(), record.key()));
                case "ticket.checked-in", "ticket.checked-out" -> service.handleScan(decoder.decode(
                        record.value(), TicketScanEvent.class, record.topic(), record.key()));
                default -> throw new InvalidEventException("Unsupported analytics topic");
            }
        } catch (DuplicateMessageException duplicate) {
            // Transaction claim trùng đã rollback trước khi consumer kết thúc lần đọc record này.
        }
    }
}
