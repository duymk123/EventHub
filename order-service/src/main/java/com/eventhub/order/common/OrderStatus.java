package com.eventhub.order.common;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    FAILED,
    EXPIRED,
    /** C19: khách tự hủy đơn chưa thanh toán để chọn lại ghế; không phát Kafka. */
    CANCELLED,
    REFUNDED
}
