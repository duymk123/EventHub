package com.eventhub.notification.messaging;

/**
 * Báo batch còn target chưa hoàn tất, thường do worker khác đang giữ lease. KafkaConfig chọn lịch chờ retry dài
 * hơn cho trường hợp này.
 */
public class DeliveryInProgressException extends RuntimeException {
    public DeliveryInProgressException() {
        super("Notification delivery is already in progress");
    }
}
