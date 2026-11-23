package com.eventhub.notification.service;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.repository.NotificationDeliveryRepository;
import com.eventhub.notification.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Ranh giới transaction cho việc tạo và hoàn tất batch. Tạo đủ các target trong một transaction giúp các lần
 * retry nhìn thấy một lô hoàn chỉnh.
 */
@Service
public class NotificationBatchService {
    private final NotificationDeliveryRepository delivery;
    private final NotificationTemplateRepository templates;
    private final TemplateRenderer renderer;
    private final Clock clock;

    public NotificationBatchService(NotificationDeliveryRepository delivery,
            NotificationTemplateRepository templates, TemplateRenderer renderer, Clock clock) {
        this.delivery = delivery;
        this.templates = templates;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** Một email cần gửi: địa chỉ và model riêng (C02: mỗi địa chỉ chỉ thấy QR của vé gửi cho mình). */
    public record EmailTarget(String email, Map<String, Object> model) {
    }

    /**
     * Giữ khóa batch theo messageId, kiểm tra fingerprint rồi trả target đã có hoặc tạo đủ target trong cùng
     * transaction: một IN_APP cho người mua (model đầy đủ) và một EMAIL cho mỗi địa chỉ. Nội dung được render và
     * lưu một lần, giá trị trả về là danh sách notificationId để delivery tiếp tục.
     */
    @Transactional
    public List<UUID> prepare(UUID messageId, String fingerprint, String code, UUID buyerId,
                              Map<String, Object> inAppModel, List<EmailTarget> emails) {
        Instant now = clock.instant();
        // Khóa theo messageId nằm trong transaction prepare, bao quanh cả bước kiểm tra và tạo target.
        delivery.lockBatch(messageId, fingerprint, 1 + emails.size(), now);
        List<UUID> existing = delivery.targets(messageId);
        if (!existing.isEmpty()) {
            // Retry dùng lại nội dung đã lưu, kể cả ADMIN đã sửa template sau khi batch được tạo.
            return existing;
        }
        create(messageId, code, NotificationChannel.IN_APP, buyerId, null, inAppModel, now);
        for (EmailTarget target : emails) {
            create(messageId, code, NotificationChannel.EMAIL, buyerId, target.email(), target.model(), now);
        }
        return delivery.targets(messageId);
    }

    private void create(UUID messageId, String code, NotificationChannel channel, UUID buyerId, String email,
                        Map<String, Object> model, Instant now) {
        var template = templates.findByCodeAndChannel(code, channel)
                .orElseThrow(() -> new IllegalStateException("Notification template unavailable"));
        String title = renderer.render(template.getSubject(), model).replaceAll("[\\r\\n]", " ");
        if (title.length() > 255) {
            throw new IllegalStateException("Rendered notification title is too long");
        }
        delivery.create(messageId, buyerId, email, channel.name(), code,
                title, renderer.render(template.getBodyTemplate(), model), model, now);
    }

    /**
     * Trong transaction ngắn, kiểm tra batch đã có đủ target và toàn bộ đã SENT. Trả true khi đã ghi dấu
     * processed_events và trạng thái COMPLETE, false khi còn việc chưa hoàn tất.
     */
    @Transactional
    public boolean complete(UUID messageId) {
        return delivery.complete(messageId, clock.instant());
    }
}
