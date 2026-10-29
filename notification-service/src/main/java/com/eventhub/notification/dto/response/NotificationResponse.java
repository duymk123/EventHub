package com.eventhub.notification.dto.response;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.common.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /notifications/me.
 * Chỉ bao gồm thông báo IN_APP; payload và mã QR không được hiển thị.
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
