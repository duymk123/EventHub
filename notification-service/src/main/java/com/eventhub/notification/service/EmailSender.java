package com.eventhub.notification.service;

import java.util.Map;

/**
 * Hợp đồng adapter gửi email. NotificationDeliveryService gọi interface này, SmtpEmailSender hiện thực việc gửi
 * qua SMTP.
 */
public interface EmailSender {
    /**
     * Gửi nội dung EMAIL đã được snapshot, payload mang dữ liệu ảnh QR khi có vé. Caller chịu trách nhiệm
     * claim/ghi trạng thái delivery quanh lời gọi này.
     */
    void send(String recipient, String subject, String html, Map<String, Object> payload);
}
