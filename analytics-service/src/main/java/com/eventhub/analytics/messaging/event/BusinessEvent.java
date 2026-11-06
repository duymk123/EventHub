package com.eventhub.analytics.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Các trường nhận diện chung của sự kiện Kafka. messageId dùng chống xử lý trùng, eventId xác định sự kiện bán
 * vé và aggregateId cung cấp ID để so với Kafka key.
 */
public interface BusinessEvent {
    /**
     * UUID duy nhất của message nguồn, được giữ nguyên qua retry/replay để consumer chống xử lý trùng.
     */
    UUID messageId();
    /**
     * Tên loại sự kiện, được EventDecoder so với tên topic thực tế nhận được.
     */
    String eventType();
    /**
     * Thời điểm nghiệp vụ gốc theo UTC, giữ nguyên khi publisher gửi lại.
     */
    Instant occurredAt();
    /**
     * UUID sự kiện bán vé mà dữ liệu nghiệp vụ thuộc về.
     */
    UUID eventId();
    /**
     * ID nghiệp vụ dùng làm Kafka key, trả orderId cho sự kiện đơn/phát vé và ticketId cho sự kiện quét.
     */
    UUID aggregateId();
}
