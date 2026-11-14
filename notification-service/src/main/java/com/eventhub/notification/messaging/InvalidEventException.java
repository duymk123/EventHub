package com.eventhub.notification.messaging;

/**
 * Báo record vi phạm hợp đồng dữ liệu. KafkaConfig phân loại lỗi này để chuyển DLQ ngay thay vì tiếp tục các
 * lần retry lỗi tạm thời.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
