package com.eventhub.analytics.service.impl;

import com.eventhub.analytics.common.EventAnalyticsSort;
import com.eventhub.analytics.common.Granularity;
import com.eventhub.analytics.dto.response.AnalyticsMetrics;
import com.eventhub.analytics.dto.response.AnalyticsOverviewResponse;
import com.eventhub.analytics.dto.response.AnalyticsSeriesPoint;
import com.eventhub.analytics.dto.response.TicketTypeTotals;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.EventMetricsRow;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.LifetimeRow;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.SalesSum;
import com.eventhub.analytics.repository.PlatformAnalyticsQueries.TicketTypeRow;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phần đọc DB của thống kê toàn hệ thống. Mỗi method public là một transaction read-only ngắn; caller
 * (PlatformAnalyticsServiceImpl) kiểm tra quyền/khoảng ngày trước và gọi HTTP sang Event/Ticket sau khi
 * transaction đã đóng.
 */
@Service
public class PlatformAnalyticsReadService {

    /** Thứ tự hạng vé hiển thị cố định; hạng lạ (nếu có) xếp sau. */
    private static final List<String> TIER_ORDER = List.of("STANDARD", "PREMIUM", "VIP");

    /** Dữ liệu một trang sự kiện trước khi ghép metadata Event/Ticket. */
    public record EventPageData(List<EventMetricsRow> rows, long total, Map<UUID, LifetimeRow> lifetime) {}

    private final PlatformAnalyticsQueries queries;
    private final ZoneId reportingZone;

    public PlatformAnalyticsReadService(PlatformAnalyticsQueries queries, Environment environment) {
        this.queries = queries;
        this.reportingZone = ZoneId.of(environment.getRequiredProperty("eventhub.analytics.reporting-zone"));
    }

    /**
     * Tổng, kỳ trước, chuỗi đã điền đủ kỳ và phân bổ hạng vé. Doanh số lấy theo metric_date (ngày VN), khán giả
     * theo ngày VN của checked_in_at; hai nguồn được gộp vào cùng bucket kỳ.
     *
     * @param eventId Chỉ để ghi lại vào response (sự kiện được lọc), không dùng để truy vấn.
     * @param scope   null = toàn hệ thống; danh sách = chỉ các sự kiện đó; rỗng = trả toàn số 0 không chạy SQL.
     */
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(LocalDate from, LocalDate to, Granularity granularity, UUID eventId,
                                              Collection<UUID> scope) {
        boolean none = scope != null && scope.isEmpty();
        Map<String, AnalyticsMetrics> buckets = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            buckets.putIfAbsent(period(day, granularity), AnalyticsMetrics.ZERO);
        }
        for (var row : none ? List.<PlatformAnalyticsQueries.DailySalesRow>of() : queries.dailySales(from, to, scope)) {
            buckets.merge(period(row.date(), granularity),
                    sales(row.revenue(), row.refunded(), row.sold(), row.refundedTickets()), AnalyticsMetrics::plus);
        }
        for (var row : none ? List.<PlatformAnalyticsQueries.DailyAttendanceRow>of()
                : queries.dailyAttendance(startOf(from), startOf(to.plusDays(1)), scope, offset(from))) {
            buckets.merge(period(row.date(), granularity), attendance(row.attendees()), AnalyticsMetrics::plus);
        }
        List<AnalyticsSeriesPoint> series = buckets.entrySet().stream()
                .map(entry -> AnalyticsSeriesPoint.of(entry.getKey(), entry.getValue()))
                .toList();
        AnalyticsMetrics totals = buckets.values().stream().reduce(AnalyticsMetrics.ZERO, AnalyticsMetrics::plus);

        // Kỳ trước có cùng số ngày, kết thúc ngay trước from.
        long length = to.toEpochDay() - from.toEpochDay() + 1;
        LocalDate previousFrom = from.minusDays(length);
        LocalDate previousTo = from.minusDays(1);
        SalesSum previousSales = none ? new SalesSum(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)
                : queries.sumSales(previousFrom, previousTo, scope);
        long previousAttendees = none ? 0 : queries.countAttendance(startOf(previousFrom), startOf(from), scope);
        AnalyticsMetrics previousTotals = sales(previousSales.revenue(), previousSales.refunded(),
                previousSales.sold(), previousSales.refundedTickets()).plus(attendance(previousAttendees));

        return new AnalyticsOverviewResponse(from.toString(), to.toString(), granularity, eventId, totals,
                previousFrom.toString(), previousTo.toString(), previousTotals, series,
                byTicketType(none ? List.of() : queries.byTicketTypeName(from, to, scope)));
    }

    /**
     * Một trang sự kiện đã sắp xếp/phân trang tại DB, tổng số dòng và số liệu lifetime của các id trong trang.
     *
     * @param scope null = mọi sự kiện; danh sách = chỉ các sự kiện đó (ORGANIZER); rỗng = trang rỗng.
     */
    @Transactional(readOnly = true)
    public EventPageData eventPage(LocalDate from, LocalDate to, Collection<UUID> scope, EventAnalyticsSort sort,
                                   int page, int size) {
        if (scope != null && scope.isEmpty()) {
            return new EventPageData(List.of(), 0, Map.of());
        }
        Instant fromUtc = startOf(from);
        Instant toUtcExclusive = startOf(to.plusDays(1));
        long total = queries.countEvents(from, to, fromUtc, toUtcExclusive, scope);
        long offset = (long) page * size;
        if (total == 0 || offset >= total) {
            return new EventPageData(List.of(), total, Map.of());
        }
        List<EventMetricsRow> rows = queries.pageEvents(from, to, fromUtc, toUtcExclusive, scope, sort, size, offset);
        Map<UUID, LifetimeRow> lifetime = queries.lifetime(rows.stream().map(EventMetricsRow::eventId).toList())
                .stream().collect(Collectors.toMap(LifetimeRow::eventId, Function.identity()));
        return new EventPageData(rows, total, lifetime);
    }

    /**
     * 00:00 ngày Việt Nam đổi sang Instant UTC, dùng làm biên của khoảng checked_in_at.
     */
    Instant startOf(LocalDate day) {
        return day.atStartOfDay(reportingZone).toInstant();
    }

    private String offset(LocalDate day) {
        ZoneOffset offset = reportingZone.getRules().getOffset(startOf(day));
        // ZoneOffset.UTC có id "Z", CONVERT_TZ cần dạng ±hh:mm.
        return offset.equals(ZoneOffset.UTC) ? "+00:00" : offset.getId();
    }

    private static String period(LocalDate day, Granularity granularity) {
        return granularity == Granularity.MONTH ? day.toString().substring(0, 7) : day.toString();
    }

    private static AnalyticsMetrics sales(BigDecimal revenue, BigDecimal refunded, long sold, long refundedTickets) {
        return new AnalyticsMetrics(revenue, refunded, revenue.subtract(refunded), sold, refundedTickets, 0);
    }

    private static AnalyticsMetrics attendance(long attendees) {
        return new AnalyticsMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, attendees);
    }

    private static List<TicketTypeTotals> byTicketType(List<TicketTypeRow> rows) {
        Map<String, TicketTypeTotals> byName = new LinkedHashMap<>();
        TIER_ORDER.forEach(name -> byName.put(name, new TicketTypeTotals(name, 0, BigDecimal.ZERO)));
        for (TicketTypeRow row : rows) {
            byName.put(row.name(), new TicketTypeTotals(row.name(), row.validTickets(), row.netRevenue()));
        }
        return new ArrayList<>(byName.values());
    }
}
