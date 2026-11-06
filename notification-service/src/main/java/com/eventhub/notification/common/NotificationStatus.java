package com.eventhub.notification.common;

/**
 * Trạng thái giao nhận PENDING, SENT hoặc FAILED. Việc người dùng đã đọc IN_APP được lưu riêng trong readAt.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
