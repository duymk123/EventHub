package com.eventhub.ticket.service;

import com.eventhub.ticket.entity.OutboxEvent;
import com.eventhub.ticket.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Đẩy outbox PENDING lên Kafka sau khi transaction nghiệp vụ commit: topic = eventType, key = aggregate_id,
 * value = payload gốc (giữ messageId khi retry). Chỉ đánh PUBLISHED sau khi broker ACK; lỗi thì dừng lô và thử
 * lại sau 30 giây theo đúng thứ tự created_at (Kafka chưa chạy thì không spam log). Consumer dedup theo messageId
 * nên gửi lặp khi crash là an toàn.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private boolean failing;
    private Instant retryAt = Instant.EPOCH;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafka, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eventhub.ticket.outbox-delay-ms:1000}")
    public void publishDue() {
        if (failing && clock.instant().isBefore(retryAt)) {
            return;
        }
        for (OutboxEvent event : repository.findTop100ByStatusOrderByCreatedAtAsc("PENDING")) {
            try {
                kafka.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                        .get(15, TimeUnit.SECONDS);
            } catch (Exception error) {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (!failing) {
                    // Chỉ log lần đầu chuyển sang lỗi để Kafka tắt không làm đầy log mỗi giây; không log payload.
                    log.warn("Outbox publish failed for {} {}, will retry: {}", event.getEventType(), event.getId(),
                            error.getClass().getSimpleName());
                    failing = true;
                }
                retryAt = clock.instant().plus(RETRY_DELAY);
                return;
            }
            event.setStatus("PUBLISHED");
            event.setPublishedAt(clock.instant());
            repository.save(event);
            if (failing) {
                log.info("Outbox publishing recovered");
                failing = false;
            }
        }
    }
}
