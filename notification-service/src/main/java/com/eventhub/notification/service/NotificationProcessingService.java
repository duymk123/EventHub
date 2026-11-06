package com.eventhub.notification.service;

import com.eventhub.notification.messaging.event.*;

/**
 * Hợp đồng nghiệp vụ mà NotificationEventConsumer gọi cho ba topic đang đăng ký.
 * NotificationProcessingServiceImpl thực hiện điều phối batch và delivery.
 */
public interface NotificationProcessingService {
    /**
     * Xử lý order.failed để thông báo cho người mua về đơn lỗi hoặc hết hạn.
     */
    void handleOrderFailed(OrderFailedEvent event);
    /**
     * Xử lý order.refunded để thông báo số tiền đã được hoàn.
     */
    void handleOrderRefunded(OrderRefundedEvent event);
    /**
     * Xử lý ticket.issued để gửi email chứa các QR và tạo IN_APP cho owner của vé.
     */
    void handleTicketIssued(TicketIssuedEvent event);
}
