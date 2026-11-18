package com.eventhub.order.service;

import com.eventhub.order.entity.OutboxEvent;
import com.eventhub.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ghi message Kafka vào outbox_events trong cùng transaction với thay đổi nghiệp vụ (docs/kafka/events.md §4).
 * outbox.id = messageId, sinh một lần; aggregate_id giữ Kafka key; payload là envelope phẳng đã serialize.
 */
@Component
public class OutboxWriter {
    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * @param eventType tên topic, ví dụ order.failed
     * @param key       Kafka key (orderId)
     * @param eventId   sự kiện mua vé/tham dự
     * @param occurredAt thời điểm nghiệp vụ gốc, không phải lúc publish
     * @param fields    trường nghiệp vụ theo payload của topic
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, UUID key, UUID eventId, Instant occurredAt, Map<String, Object> fields) {
        UUID messageId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("messageId", messageId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", occurredAt);
        envelope.put("eventId", eventId);
        envelope.putAll(fields);

        OutboxEvent event = new OutboxEvent();
        event.setId(messageId);
        event.setAggregateId(key);
        event.setEventType(eventType);
        event.setCreatedAt(occurredAt);
        try {
            event.setPayload(mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize outbox payload " + eventType, error);
        }
        repository.save(event);
    }
}
