package com.eventhub.analytics.service;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.EventAnalyticsSort;
import com.eventhub.analytics.common.Granularity;
import com.eventhub.analytics.dto.response.AnalyticsOverviewResponse;
import com.eventhub.analytics.dto.response.EventAnalyticsRow;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thống kê gộp cho trang Phân tích (C10, plan Analytics §8). ADMIN xem mọi sự kiện, ORGANIZER chỉ sự kiện mình
 * sở hữu, CUSTOMER bị 403.
 */
public interface PlatformAnalyticsService {

    /**
     * GET /api/v1/analytics/overview. Không có eventId: ADMIN = toàn hệ thống, ORGANIZER = tổng sự kiện của mình.
     * Có eventId: ADMIN hoặc ORGANIZER owner.
     */
    AnalyticsOverviewResponse getOverview(Actor actor, LocalDate from, LocalDate to, Granularity granularity,
                                          UUID eventId);

    /**
     * GET /api/v1/analytics/events. ADMIN = mọi sự kiện, ORGANIZER = sự kiện của mình; page từ 0, size 1..100.
     */
    Page<EventAnalyticsRow> listEvents(Actor actor, LocalDate from, LocalDate to, EventAnalyticsSort sort,
                                       int page, int size);
}
