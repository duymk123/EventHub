package com.eventhub.notification.dto.response;

import com.eventhub.notification.common.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi cho các endpoint danh sách và chi tiết template.
 */
public record TemplateResponse(
        UUID id,
        String code,
        NotificationChannel channel,
        String subject,
        String bodyTemplate,
        Instant updatedAt
) {}
