package com.eventhub.analytics.service;

import java.util.UUID;

/**
 * Tín hiệu nội bộ Spring mang eventId khi projection thay đổi. DashboardPushService tiếp nhận tại AFTER_COMMIT
 * để gửi snapshot đã commit cho client.
 *
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 */
public record DashboardChangedEvent(UUID eventId) {}
