package com.eventhub.notification.service;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.dto.response.NotificationResponse;
import com.eventhub.notification.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Truy vấn và cập nhật trạng thái đã đọc cho thông báo trong ứng dụng của người dùng.
 */
public interface NotificationService {

    Page<NotificationResponse> getMine(Actor actor, boolean unreadOnly, Pageable pageable);

    void markRead(Actor actor, UUID notificationId);

    UnreadCountResponse countUnread(Actor actor);
}
