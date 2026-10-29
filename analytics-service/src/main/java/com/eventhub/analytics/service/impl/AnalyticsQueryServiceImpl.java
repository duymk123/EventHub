package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.ErrorCode;
import com.eventhub.analytics.dto.response.*;
import com.eventhub.analytics.entity.DashboardSnapshot;
import com.eventhub.analytics.exception.BusinessException;
import com.eventhub.analytics.repository.DashboardSnapshotRepository;
import com.eventhub.analytics.repository.SalesMetricRepository;
import com.eventhub.analytics.repository.SalesMetricRepository.DailySalesRow;
import com.eventhub.analytics.repository.SalesMetricRepository.SalesTotals;
import com.eventhub.analytics.repository.SalesMetricRepository.TicketTypeSalesRow;
import com.eventhub.analytics.service.AnalyticsQueryService;
import com.eventhub.analytics.service.EventAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service truy vấn chỉ đọc cho các endpoint phân tích.
 * Kiểm tra quyền truy cập trước khi đọc dữ liệu phân tích.
 */
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final EventAccessService eventAccessService;
    private final SalesMetricRepository salesMetricRepository;
    private final DashboardSnapshotRepository dashboardSnapshotRepository;

    public AnalyticsQueryServiceImpl(EventAccessService eventAccessService,
                                     SalesMetricRepository salesMetricRepository,
                                     DashboardSnapshotRepository dashboardSnapshotRepository) {
        this.eventAccessService = eventAccessService;
        this.salesMetricRepository = salesMetricRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
    }

    /**
     * GET /api/v1/analytics/events/{id}/sales?from=&to=
     * Mục đích: Xem báo cáo doanh số sự kiện theo khoảng thời gian, chi tiết theo ngày và hạng vé.
     * Kiểm tra quyền → xác thực from ≤ to → truy vấn chỉ đọc tổng hợp.
     * Nếu thiếu cận trên/dưới thì không giới hạn.
     */
    @Override
    @Transactional(readOnly = true)
    public SalesReportResponse getSales(Actor actor, UUID eventId, LocalDate from, LocalDate to) {
        eventAccessService.assertCanView(actor, eventId);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "from date must not be after to date");
        }

        SalesTotals totals = salesMetricRepository.sumTotals(eventId, from, to);
        BigDecimal netRevenue = totals.totalRevenue().subtract(totals.totalRefunded());

        List<DailySalesRow> dailyRows = salesMetricRepository.aggregateDaily(eventId, from, to);
        List<DailySales> daily = dailyRows.stream().map(row -> new DailySales(
                row.date().toString(),
                row.revenue(),
                row.refundedAmount(),
                row.revenue().subtract(row.refundedAmount()),
                row.ticketsSold(),
                row.ticketsRefunded()
        )).toList();

        List<TicketTypeSalesRow> typeRows = salesMetricRepository.aggregateByTicketType(eventId, from, to);
        List<TicketTypeSales> byTicketType = typeRows.stream().map(row -> new TicketTypeSales(
                row.ticketTypeId(),
                row.ticketTypeName(),
                row.revenue(),
                row.refundedAmount(),
                row.revenue().subtract(row.refundedAmount()),
                row.ticketsSold(),
                row.ticketsRefunded()
        )).toList();

        return new SalesReportResponse(
                eventId,
                from != null ? from.toString() : null,
                to != null ? to.toString() : null,
                totals.totalRevenue(),
                totals.totalRefunded(),
                netRevenue,
                totals.totalTicketsSold(),
                totals.totalTicketsRefunded(),
                daily,
                byTicketType
        );
    }

    /**
     * GET /api/v1/analytics/events/{id}/attendance
     * Mục đích: Xem số người đang có mặt và tổng số vé đã check-in của sự kiện.
     * Kiểm tra quyền rồi đọc snapshot; nếu không có dữ liệu projection, trả về số 0.
     */
    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getAttendance(Actor actor, UUID eventId) {
        eventAccessService.assertCanView(actor, eventId);

        DashboardSnapshot snapshot = dashboardSnapshotRepository.findByEventId(eventId).orElse(null);
        if (snapshot == null) {
            return new AttendanceResponse(eventId, 0, null, 0);
        }
        return new AttendanceResponse(
                snapshot.getEventId(),
                snapshot.getCurrentAttendance(),
                snapshot.getLastUpdated(),
                snapshot.getTotalCheckedIn()
        );
    }

    /**
     * GET /api/v1/analytics/events/{id}/dashboard
     * Mục đích: Lấy số liệu tổng quan ban đầu cho dashboard trước khi nhận cập nhật qua WebSocket.
     * Cùng DTO được dùng bởi WebSocket push. Không có projection → số 0 và thời gian null.
     */
    @Override
    @Transactional(readOnly = true)
    public DashboardSnapshotResponse getDashboard(Actor actor, UUID eventId) {
        eventAccessService.assertCanView(actor, eventId);

        DashboardSnapshot snapshot = dashboardSnapshotRepository.findByEventId(eventId).orElse(null);
        if (snapshot == null) {
            return new DashboardSnapshotResponse(
                    eventId,
                    BigDecimal.ZERO,
                    0,
                    0,
                    0,
                    null
            );
        }
        return new DashboardSnapshotResponse(
                snapshot.getEventId(),
                snapshot.getTotalRevenue(),
                snapshot.getTotalTicketsSold(),
                snapshot.getCurrentAttendance(),
                snapshot.getTotalCheckedIn(),
                snapshot.getLastUpdated()
        );
    }
}
