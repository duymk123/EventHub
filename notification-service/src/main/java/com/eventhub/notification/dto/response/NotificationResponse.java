package com.eventhub.notification.dto.response;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.common.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /notifications/me.
 * Chỉ bao gồm thông báo IN_APP; payload và mã QR không được hiển thị.
 *
 * @param id UUID thông báo dùng khi gọi API đánh dấu đã đọc.
 * @param channel Kênh EMAIL hoặc IN_APP của mẫu hoặc thông báo.
 * @param templateCode Mã template đã dùng để tạo snapshot thông báo.
 * @param title Tiêu đề đã render và lưu cho lần gửi này.
 * @param content Nội dung đã render và lưu cho lần gửi này.
 * @param status Trạng thái giao nhận PENDING, SENT hoặc FAILED.
 * @param readAt Thời điểm người dùng đánh dấu IN_APP đã đọc, null khi chưa đọc.
 * @param createdAt Thời điểm tạo bản ghi UTC.
 */
public record NotificationResponse(
        UUID id,
        NotificationChannel channel,
        String templateCode,
        String title,
        String content,
        NotificationStatus status,
        Instant readAt,
        Instant createdAt
) {}
