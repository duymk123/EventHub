package com.eventhub.analytics.client;

import java.util.UUID;

/**
 * Giao diện gọi API nội bộ của Event Service.
 * Implementation sử dụng thông tin xác thực S2S khi được cấu hình.
 */
public interface EventClient {
    /**
     * Lấy metadata event bằng credential nội bộ, phục vụ kiểm tra quyền xem báo cáo.
     */
    InternalEventResponse get(UUID eventId);
}
