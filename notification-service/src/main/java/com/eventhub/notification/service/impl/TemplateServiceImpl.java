package com.eventhub.notification.service.impl;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.common.BusinessRole;
import com.eventhub.notification.common.ErrorCode;
import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.dto.request.TemplateRequest;
import com.eventhub.notification.dto.response.TemplateResponse;
import com.eventhub.notification.entity.NotificationTemplate;
import com.eventhub.notification.exception.BusinessException;
import com.eventhub.notification.repository.NotificationTemplateRepository;
import com.eventhub.notification.service.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý template dành cho admin.
 * Chỉ vai trò ADMIN được phép.
 */
@Service
public class TemplateServiceImpl implements TemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.eventhub.notification.service.TemplateRenderer renderer;

    public TemplateServiceImpl(NotificationTemplateRepository templateRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.eventhub.notification.service.TemplateRenderer renderer) {
        this.templateRepository = templateRepository;
        this.jdbc = jdbc;
        this.renderer = renderer;
    }

    /**
     * Kiểm tra business role trước mọi thao tác quản lý mẫu.
     */
    private void assertAdmin(Actor actor) {
        if (actor.role() != BusinessRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only ADMIN can manage templates");
        }
    }

    /**
     * GET /api/v1/admin/notification-templates
     * Mục đích: Danh sách mẫu thông báo, chỉ dành cho ADMIN.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Actor actor) {
        // Kiểm tra business role trước mọi thao tác quản lý mẫu.
        assertAdmin(actor);
        return templateRepository.findAllByOrderByCodeAscChannelAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * GET /api/v1/admin/notification-templates/{code}?channel=
     * Mục đích: Xem chi tiết một mẫu thông báo theo mã và kênh gửi, chỉ dành cho ADMIN.
     */
    @Override
    @Transactional(readOnly = true)
    public TemplateResponse get(Actor actor, String code, NotificationChannel channel) {
        // Kiểm tra business role trước mọi thao tác quản lý mẫu.
        assertAdmin(actor);
        NotificationTemplate template = templateRepository.findByCodeAndChannel(code, channel)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TEMPLATE_NOT_FOUND,
                        "Template not found: code=" + code + ", channel=" + channel));
        // Map mẫu đang lưu thành DTO để ADMIN xem trên giao diện.
        return toResponse(template);
    }

    /**
     * PUT /api/v1/admin/notification-templates/{code}
     * Mục đích: Thêm mới hoặc cập nhật mẫu thông báo theo mã và kênh gửi, chỉ dành cho ADMIN.
     * Tìm template hiện có theo (code, channel) và cập nhật, hoặc tạo mới.
     */
    @Override
    @Transactional
    public TemplateResponse upsert(Actor actor, String code, TemplateRequest request) {
        // Kiểm tra business role trước mọi thao tác quản lý mẫu.
        assertAdmin(actor);

        NotificationChannel channel = request.channel();

        try {
            renderer.validate(request.subject());
            renderer.validate(request.bodyTemplate());
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid notification template");
        }
        // UPSERT được DB thực hiện atomic, id giữ nguyên nếu code/channel đã tồn tại.
        jdbc.update("""
                INSERT INTO notification_templates(id, code, channel, subject, body_template, updated_at)
                VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE subject = VALUES(subject),
                    body_template = VALUES(body_template), updated_at = VALUES(updated_at)
                """, UUID.randomUUID().toString(), code, channel.name(), request.subject(), request.bodyTemplate(),
                java.sql.Timestamp.from(java.time.Instant.now()));
        // Map mẫu đang lưu thành DTO để ADMIN xem trên giao diện.
        return toResponse(templateRepository.findByCodeAndChannel(code, channel).orElseThrow());
    }

    /**
     * Map mẫu đang lưu thành DTO để ADMIN xem trên giao diện.
     */
    private TemplateResponse toResponse(NotificationTemplate t) {
        return new TemplateResponse(
                t.getId(),
                t.getCode(),
                t.getChannel(),
                t.getSubject(),
                t.getBodyTemplate(),
                t.getUpdatedAt()
        );
    }
}
