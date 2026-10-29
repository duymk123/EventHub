package com.eventhub.analytics.service;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.dto.response.AttendanceResponse;
import com.eventhub.analytics.dto.response.DashboardSnapshotResponse;
import com.eventhub.analytics.dto.response.SalesReportResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Các truy vấn chỉ đọc cho số liệu phân tích của các sự kiện mà người dùng được phép xem.
 */
public interface AnalyticsQueryService {

    SalesReportResponse getSales(Actor actor, UUID eventId, LocalDate from, LocalDate to);

    AttendanceResponse getAttendance(Actor actor, UUID eventId);

    DashboardSnapshotResponse getDashboard(Actor actor, UUID eventId);
}
