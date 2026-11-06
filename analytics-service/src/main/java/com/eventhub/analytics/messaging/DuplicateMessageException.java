package com.eventhub.analytics.messaging;

/**
 * Tín hiệu riêng cho INSERT claim trùng messageId. Consumer nhận diện tín hiệu này sau ranh giới transaction để
 * kết thúc record đã xử lý.
 */
public class DuplicateMessageException extends RuntimeException {
    public DuplicateMessageException() {
        super("Event already processed");
    }
}
