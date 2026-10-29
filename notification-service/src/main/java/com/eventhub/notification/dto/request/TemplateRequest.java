package com.eventhub.notification.dto.request;

import com.eventhub.notification.common.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của request cho PUT /admin/notification-templates/{code}.
 */
public record TemplateRequest(
        @NotNull NotificationChannel channel,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String bodyTemplate
) {}
