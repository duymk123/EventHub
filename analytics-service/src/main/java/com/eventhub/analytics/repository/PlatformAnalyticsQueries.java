package com.eventhub.analytics.repository;

import com.eventhub.analytics.common.EventAnalyticsSort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Truy vấn tổng hợp cho trang Phân tích (C10, plan Analytics §8.6). Chỉ đọc sales_metrics, attendance_state và
 * dashboard_snapshot của Analytics; không JOIN DB service khác.
 *
 * <p>Tham số {@code scope} là phạm vi sự kiện: {@code null} = toàn hệ thống (ADMIN), danh sách = chỉ các
 * event_id đó (một sự kiện được chọn, hoặc mọi sự kiện của một ORGANIZER). Danh sách rỗng không hợp lệ ở đây vì
 * {@code IN ()} là lỗi cú pháp; service phải tự trả kết quả 0 khi phạm vi rỗng.
 *
 * <p>Khoảng thời gian check-in nhận biên UTC đã tính sẵn ở service để dùng được index trên checked_in_at.
 */
@Repository
public class PlatformAnalyticsQueries {

    /** Một ngày Việt Nam của sales_metrics. */
    public record DailySalesRow(LocalDate date, BigDecimal revenue, BigDecimal refunded, long sold, long refundedTickets) {}

    /** Số vé check-in lần đầu trong một ngày Việt Nam. */
    public record DailyAttendanceRow(LocalDate date, long attendees) {}

    /** Tổng theo tên hạng vé snapshot. */
    public record TicketTypeRow(String name, long validTickets, BigDecimal netRevenue) {}

    /** Tổng doanh số của một khoảng ngày. */
    public record SalesSum(BigDecimal revenue, BigDecimal refunded, long sold, long refundedTickets) {}

    /** Số liệu của một sự kiện trong khoảng lọc, chưa có metadata Event/Ticket. */
    public record EventMetricsRow(UUID eventId, BigDecimal gross, BigDecimal refund, BigDecimal net,
                                  long sold, long refundedTickets, long attendees) {}

    /** Số liệu toàn thời gian của một sự kiện để tính tỉ lệ check-in. */
    public record LifetimeRow(UUID eventId, long ticketsSold, long checkedIn, long ticketsRefunded) {}

    // :allEvents = 1 bỏ qua lọc; khi đó :eventIds chỉ là giá trị giữ chỗ để câu SQL luôn hợp lệ.
    private static final String SCOPE = " AND (:allEvents = 1 OR event_id IN (:eventIds))";
    private static final String SALES_FILTER = """
             FROM sales_metrics
            WHERE metric_date BETWEEN :from AND :to""" + SCOPE + "\n";
    private static final String ATTENDANCE_FILTER = """
             FROM attendance_state
            WHERE checked_in_at >= :fromUtc AND checked_in_at < :toUtcExclusive""" + SCOPE + "\n";
    // CTE dùng chung cho trang và đếm tổng: các sự kiện trong phạm vi có doanh số, hoàn tiền hoặc check-in.
    private static final String EVENT_CTE = "WITH s AS (\n"
            + "  SELECT event_id, SUM(revenue) gross, SUM(refunded_amount) refund,\n"
            + "         SUM(tickets_sold) sold, SUM(tickets_refunded) sold_refunded\n"
            + SALES_FILTER + "   GROUP BY event_id),\n"
            + "a AS (\n"
            + "  SELECT event_id, COUNT(*) att\n"
            + ATTENDANCE_FILTER + "   GROUP BY event_id),\n"
            + "ids AS (SELECT event_id FROM s UNION SELECT event_id FROM a)\n";

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformAnalyticsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Doanh số theo metric_date (đã là ngày Việt Nam). Service gộp tháng và điền kỳ trống.
     */
    public List<DailySalesRow> dailySales(LocalDate from, LocalDate to, Collection<UUID> scope) {
        return jdbc.query("SELECT metric_date, SUM(revenue), SUM(refunded_amount), SUM(tickets_sold), "
                        + "SUM(tickets_refunded)" + SALES_FILTER + " GROUP BY metric_date",
                dateParams(from, to, scope),
                (row, i) -> new DailySalesRow(row.getObject(1, LocalDate.class), row.getBigDecimal(2),
                        row.getBigDecimal(3), row.getLong(4), row.getLong(5)));
    }

    /**
     * Đếm vé check-in theo ngày Việt Nam. CONVERT_TZ dùng offset cố định (không cần bảng time zone MySQL), đòi
     * hỏi session JDBC ở UTC như cấu hình datasource.
     */
    public List<DailyAttendanceRow> dailyAttendance(Instant fromUtc, Instant toUtcExclusive, Collection<UUID> scope,
                                                    String offset) {
        MapSqlParameterSource params = instantParams(fromUtc, toUtcExclusive, scope).addValue("offset", offset);
        return jdbc.query("SELECT DATE(CONVERT_TZ(checked_in_at, '+00:00', :offset)) d, COUNT(*)"
                        + ATTENDANCE_FILTER + " GROUP BY d",
                params, (row, i) -> new DailyAttendanceRow(row.getObject(1, LocalDate.class), row.getLong(2)));
    }

    /**
     * Gom theo tên hạng vé vì mỗi sự kiện có ticket_type_id riêng.
     */
    public List<TicketTypeRow> byTicketTypeName(LocalDate from, LocalDate to, Collection<UUID> scope) {
        return jdbc.query("SELECT ticket_type_name, SUM(tickets_sold - tickets_refunded), "
                        + "SUM(revenue - refunded_amount)" + SALES_FILTER + " GROUP BY ticket_type_name",
                dateParams(from, to, scope),
                (row, i) -> new TicketTypeRow(row.getString(1), row.getLong(2), row.getBigDecimal(3)));
    }

    /**
     * Tổng doanh số của khoảng, dùng cho kỳ trước. COALESCE trả 0 khi không có dòng.
     */
    public SalesSum sumSales(LocalDate from, LocalDate to, Collection<UUID> scope) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(revenue), 0), COALESCE(SUM(refunded_amount), 0), "
                        + "COALESCE(SUM(tickets_sold), 0), COALESCE(SUM(tickets_refunded), 0)" + SALES_FILTER,
                dateParams(from, to, scope),
                (row, i) -> new SalesSum(row.getBigDecimal(1), row.getBigDecimal(2), row.getLong(3), row.getLong(4)));
    }

    /**
     * Tổng vé check-in của khoảng, dùng cho kỳ trước.
     */
    public long countAttendance(Instant fromUtc, Instant toUtcExclusive, Collection<UUID> scope) {
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + ATTENDANCE_FILTER,
                instantParams(fromUtc, toUtcExclusive, scope), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Một trang sự kiện sắp xếp giảm dần theo cột whitelist, tie-break event_id tăng dần để trang ổn định.
     */
    public List<EventMetricsRow> pageEvents(LocalDate from, LocalDate to, Instant fromUtc, Instant toUtcExclusive,
                                            Collection<UUID> scope, EventAnalyticsSort sort, int limit, long offset) {
        MapSqlParameterSource params = eventParams(from, to, fromUtc, toUtcExclusive, scope)
                .addValue("limit", limit).addValue("offset", offset);
        // sort.column() là alias cố định trong enum, không lấy từ chuỗi request.
        return jdbc.query(EVENT_CTE + """
                SELECT ids.event_id, COALESCE(s.gross, 0) gross, COALESCE(s.refund, 0) refund,
                       COALESCE(s.gross, 0) - COALESCE(s.refund, 0) net,
                       COALESCE(s.sold, 0) sold, COALESCE(s.sold_refunded, 0) sold_refunded, COALESCE(a.att, 0) att
                  FROM ids
                  LEFT JOIN s ON s.event_id = ids.event_id
                  LEFT JOIN a ON a.event_id = ids.event_id
                 ORDER BY\s""" + sort.column() + " DESC, ids.event_id ASC LIMIT :limit OFFSET :offset",
                params,
                (row, i) -> new EventMetricsRow(UUID.fromString(row.getString(1)), row.getBigDecimal(2),
                        row.getBigDecimal(3), row.getBigDecimal(4), row.getLong(5), row.getLong(6), row.getLong(7)));
    }

    /**
     * Tổng số sự kiện trong phạm vi có phát sinh trong khoảng, dùng cho page.totalElements.
     */
    public long countEvents(LocalDate from, LocalDate to, Instant fromUtc, Instant toUtcExclusive,
                            Collection<UUID> scope) {
        Long count = jdbc.queryForObject(EVENT_CTE + "SELECT COUNT(*) FROM ids",
                eventParams(from, to, fromUtc, toUtcExclusive, scope), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Số liệu toàn thời gian cho các sự kiện của trang hiện tại. Sự kiện chưa có snapshot không có trong kết quả,
     * service coi là 0.
     */
    public List<LifetimeRow> lifetime(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = eventIds.stream().map(UUID::toString).toList();
        return jdbc.query("""
                SELECT d.event_id, d.total_tickets_sold, d.total_checked_in, COALESCE(r.refunded, 0)
                  FROM dashboard_snapshot d
                  LEFT JOIN (SELECT event_id, SUM(tickets_refunded) refunded FROM sales_metrics
                              WHERE event_id IN (:ids) GROUP BY event_id) r ON r.event_id = d.event_id
                 WHERE d.event_id IN (:ids)
                """,
                new MapSqlParameterSource("ids", ids),
                (row, i) -> new LifetimeRow(UUID.fromString(row.getString(1)), row.getLong(2), row.getLong(3),
                        row.getLong(4)));
    }

    private static MapSqlParameterSource withScope(MapSqlParameterSource params, Collection<UUID> scope) {
        if (scope != null && scope.isEmpty()) {
            throw new IllegalArgumentException("Empty event scope must be handled by the caller");
        }
        return params
                .addValue("allEvents", scope == null ? 1 : 0)
                .addValue("eventIds", scope == null ? List.of("") : scope.stream().map(UUID::toString).toList());
    }

    private MapSqlParameterSource dateParams(LocalDate from, LocalDate to, Collection<UUID> scope) {
        return withScope(new MapSqlParameterSource()
                .addValue("from", from, Types.DATE)
                .addValue("to", to, Types.DATE), scope);
    }

    private MapSqlParameterSource instantParams(Instant fromUtc, Instant toUtcExclusive, Collection<UUID> scope) {
        return withScope(new MapSqlParameterSource()
                .addValue("fromUtc", Timestamp.from(fromUtc), Types.TIMESTAMP)
                .addValue("toUtcExclusive", Timestamp.from(toUtcExclusive), Types.TIMESTAMP), scope);
    }

    private MapSqlParameterSource eventParams(LocalDate from, LocalDate to, Instant fromUtc, Instant toUtcExclusive,
                                              Collection<UUID> scope) {
        return withScope(new MapSqlParameterSource()
                .addValue("from", from, Types.DATE)
                .addValue("to", to, Types.DATE)
                .addValue("fromUtc", Timestamp.from(fromUtc), Types.TIMESTAMP)
                .addValue("toUtcExclusive", Timestamp.from(toUtcExclusive), Types.TIMESTAMP), scope);
    }
}
