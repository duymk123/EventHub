package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.dto.response.*;
import com.eventhub.analytics.service.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Điều phối truy vấn REST theo thứ tự kiểm tra quyền qua EventAccessService rồi đọc DB qua
 * AnalyticsReportReadService.
 */
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {
    private final EventAccessService access;
    private final AnalyticsReportReadService reader;

    public AnalyticsQueryServiceImpl(EventAccessService access, AnalyticsReportReadService reader) {
        this.access = access;
        this.reader = reader;
    }

    /**
     * Xác minh quyền/tồn tại qua EventAccessService trước, sau đó gọi reader mở transaction read-only và tính
     * báo cáo.
     */
    @Override
    public SalesReportResponse getSales(Actor actor, UUID eventId, LocalDate from, LocalDate to) {
        access.assertCanView(actor, eventId);
        return reader.getSales(eventId, from, to);
    }

    /**
     * Kiểm tra quyền theo event rồi đọc attendance từ snapshot.
     */
    @Override
    public AttendanceResponse getAttendance(Actor actor, UUID eventId) {
        access.assertCanView(actor, eventId);
        return reader.getAttendance(eventId);
    }

    /**
     * Kiểm tra quyền theo event rồi đọc DTO tổng quan dùng chung với WebSocket.
     */
    @Override
    public DashboardSnapshotResponse getDashboard(Actor actor, UUID eventId) {
        access.assertCanView(actor, eventId);
        return reader.getDashboard(eventId);
    }
}
