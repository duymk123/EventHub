package com.eventhub.analytics.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Giao diện gọi API nội bộ của Event Service.
 * Implementation sử dụng thông tin xác thực S2S khi được cấu hình.
 */
public interface EventClient {
    /** Số id tối đa cho một lần gọi GET /internal/events?ids= (INT-02). */
    int MAX_BATCH = 100;

    /**
     * Lấy metadata event bằng credential nội bộ, phục vụ kiểm tra quyền xem báo cáo.
     */
    InternalEventResponse get(UUID eventId);

    /**
     * INT-02 (C10): lấy metadata nhiều event trong một lần gọi, tối đa {@link #MAX_BATCH} id. Id không tồn tại
     * không có trong map. Lỗi Event Service ném EVENT_SERVICE_UNAVAILABLE.
     */
    Map<UUID, InternalEventResponse> getBatch(Collection<UUID> eventIds);

    /**
     * INT-03 (C10): id mọi sự kiện (mọi status) của một organizer, dùng làm phạm vi thống kê gộp cho ORGANIZER.
     * Organizer chưa có sự kiện trả danh sách rỗng. Lỗi Event Service ném EVENT_SERVICE_UNAVAILABLE.
     */
    List<UUID> getIdsByOrganizer(UUID organizerId);
}
