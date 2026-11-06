package com.eventhub.notification.common;

/**
 * Hai kênh giao nhận hiện tại. IN_APP được đọc từ DB bằng REST, EMAIL được gửi qua adapter SMTP.
 */
public enum NotificationChannel {
    EMAIL,
    IN_APP
}
