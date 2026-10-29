package com.eventhub.notification.service.impl;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.common.ErrorCode;
import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.dto.response.NotificationResponse;
import com.eventhub.notification.dto.response.UnreadCountResponse;
import com.eventhub.notification.entity.Notification;
import com.eventhub.notification.exception.BusinessException;
import com.eventhub.notification.repository.NotificationRepository;
import com.eventhub.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service truy vấn và thay đổi thông báo dành cho người dùng.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * GET /api/v1/notifications/me?unreadOnly=&page=&size=&sort=
     * Mục đích: Danh sách thông báo in-app của tôi, có lọc chưa đọc và phân trang.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMine(Actor actor, boolean unreadOnly, Pageable pageable) {
        Page<Notification> result;
        if (unreadOnly) {
            result = notificationRepository.findByRecipientUserIdAndChannelAndReadAtIsNull(
                    actor.userId(), NotificationChannel.IN_APP, pageable);
        } else {
            result = notificationRepository.findByRecipientUserIdAndChannel(
                    actor.userId(), NotificationChannel.IN_APP, pageable);
        }

        return result.map(this::toResponse);
    }

    /**
     * PUT /api/v1/notifications/{id}/read
     * Mục đích: Đánh dấu một thông báo in-app của tôi là đã đọc, gọi lại không đổi thời điểm đọc đầu tiên.
     * Trả về 204 nếu đã đọc hoặc vừa đánh dấu; 404 nếu không tìm thấy hoặc không thuộc về người dùng.
     */
    @Override
    @Transactional
    public void markRead(Actor actor, UUID notificationId) {
        // Trước tiên, xác minh thông báo tồn tại và thuộc về người dùng
        Notification notification = notificationRepository
                .findByIdAndRecipientUserIdAndChannel(
                        notificationId, actor.userId(), NotificationChannel.IN_APP)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND,
                        "Notification not found"));

        // Nếu đã đọc, vẫn trả về thành công (idempotent)
        if (notification.getReadAt() != null) {
            return;
        }

        int updated = notificationRepository.markReadIfUnread(
                notificationId, actor.userId(), NotificationChannel.IN_APP, Instant.now());

        // Nếu 0 dòng được cập nhật và ta biết nó tồn tại, thì đã được đọc bởi thread khác
        // Điều này vẫn là thành công (idempotent)
    }

    /**
     * GET /api/v1/notifications/me/unread-count
     * Mục đích: Đếm số thông báo in-app chưa đọc của tôi.
     */
    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(Actor actor) {
        long count = notificationRepository.countByRecipientUserIdAndChannelAndReadAtIsNull(
                actor.userId(), NotificationChannel.IN_APP);
        return new UnreadCountResponse((int) count);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getChannel(),
                n.getTemplateCode(),
                n.getTitle(),
                n.getContent(),
                n.getStatus(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
