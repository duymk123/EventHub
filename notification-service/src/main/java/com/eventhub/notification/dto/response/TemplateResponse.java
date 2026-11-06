package com.eventhub.notification.dto.response;

import com.eventhub.notification.common.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi cho các endpoint danh sách và chi tiết template.
 *
 * @param id UUID bản ghi template được ADMIN quản lý.
 * @param code Mã template nghiệp vụ dùng để chọn mẫu.
 * @param channel Kênh EMAIL hoặc IN_APP của mẫu hoặc thông báo.
 * @param subject Mẫu tiêu đề được render khi chuẩn bị notification.
 * @param bodyTemplate Mẫu Mustache chứa biến của nội dung thông báo.
 * @param updatedAt Mốc cập nhật nội dung template gần nhất.
 */
public record TemplateResponse(
        UUID id,
        String code,
        NotificationChannel channel,
        String subject,
        String bodyTemplate,
        Instant updatedAt
) {}
