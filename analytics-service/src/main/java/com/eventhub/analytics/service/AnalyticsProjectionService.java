package com.eventhub.analytics.service;

import com.eventhub.analytics.messaging.event.*;

/**
 * Hợp đồng cập nhật read model từ sự kiện Kafka. AnalyticsProjectionServiceImpl thực hiện transaction và phát
 * tín hiệu dashboard sau mỗi thay đổi hợp lệ.
 */
public interface AnalyticsProjectionService {
    /**
     * Áp dụng order.paid một lần vào metric và snapshot.
     */
    void handlePaid(OrderPaidEvent event);
    /**
     * Áp dụng phân bổ tiền/vé hoàn từ order.refunded vào báo cáo.
     */
    void handleRefunded(OrderRefundedEvent event);
    /**
     * Áp dụng bằng chứng ticket.checked-in hoặc ticket.checked-out vào attendance.
     */
    void handleScan(TicketScanEvent event);
}
