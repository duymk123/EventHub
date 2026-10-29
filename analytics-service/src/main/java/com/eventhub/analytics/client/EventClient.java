package com.eventhub.analytics.client;

import java.util.UUID;

/**
 * Giao diện gọi API nội bộ của Event Service.
 * Implementation sử dụng thông tin xác thực S2S khi được cấu hình.
 */
public interface EventClient {
    InternalEventResponse get(UUID eventId);
}
