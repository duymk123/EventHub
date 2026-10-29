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

    public TemplateServiceImpl(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

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
        assertAdmin(actor);
        NotificationTemplate template = templateRepository.findByCodeAndChannel(code, channel)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TEMPLATE_NOT_FOUND,
                        "Template not found: code=" + code + ", channel=" + channel));
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
        assertAdmin(actor);

        NotificationChannel channel = request.channel();

        // Tìm template hiện có với cùng (code, channel)
        NotificationTemplate template = templateRepository.findByCodeAndChannel(code, channel)
                .orElseGet(() -> {
                    NotificationTemplate newTemplate = new NotificationTemplate();
                    newTemplate.setId(UUID.randomUUID());
                    newTemplate.setCode(code);
                    newTemplate.setChannel(channel);
                    return newTemplate;
                });

        // Cập nhật các trường
        template.setSubject(request.subject());
        template.setBodyTemplate(request.bodyTemplate());

        NotificationTemplate saved = templateRepository.save(template);
        return toResponse(saved);
    }

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
