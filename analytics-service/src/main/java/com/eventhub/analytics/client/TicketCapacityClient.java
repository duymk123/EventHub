package com.eventhub.analytics.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi API nội bộ của Ticket Service để lấy sức chứa (SUM total_quota) theo sự kiện (C10).
 */
public interface TicketCapacityClient {
    /** Số id tối đa cho một lần gọi. */
    int MAX_BATCH = 100;

    /**
     * GET /internal/ticket-types/capacity?eventIds=. Sức chứa chỉ để hiển thị, nên khi Ticket Service lỗi
     * implementation trả map rỗng (capacity=null ở API) thay vì làm hỏng cả bảng thống kê.
     */
    Map<UUID, Integer> getCapacity(Collection<UUID> eventIds);
}
