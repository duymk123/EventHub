package com.eventhub.notification.dto.request;

import com.eventhub.notification.common.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của request cho PUT /admin/notification-templates/{code}.
 *
 * @param channel Kênh EMAIL hoặc IN_APP của mẫu hoặc thông báo.
 * @param subject Mẫu tiêu đề được render khi chuẩn bị notification.
 * @param bodyTemplate Mẫu Mustache chứa biến của nội dung thông báo.
 */
public record TemplateRequest(
        @NotNull NotificationChannel channel,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String bodyTemplate
) {}
