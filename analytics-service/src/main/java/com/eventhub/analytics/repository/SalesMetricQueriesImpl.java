package com.eventhub.analytics.repository;

import com.eventhub.analytics.repository.SalesMetricRepository.*;
import org.springframework.jdbc.core.namedparam.*;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Thực thi các câu SQL tổng hợp cho báo cáo sales. Named parameters truyền eventId/from/to, mapper JDBC chuyển
 * CHAR(36), DATE và DECIMAL về kiểu Java tương ứng.
 */
public class SalesMetricQueriesImpl implements SalesMetricQueries {
    private static final String FILTER = """
            FROM sales_metrics WHERE event_id = :eventId
              AND (:from IS NULL OR metric_date >= :from) AND (:to IS NULL OR metric_date <= :to)
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public SalesMetricQueriesImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Cộng doanh thu, tiền hoàn và số vé trong khoảng ngày của một event, COALESCE trả số 0 khi chưa có metric.
     */
    @Override
    public SalesTotals sumTotals(UUID eventId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(revenue), 0), COALESCE(SUM(refunded_amount), 0),
                    COALESCE(SUM(tickets_sold), 0), COALESCE(SUM(tickets_refunded), 0)
                """ + FILTER, params(eventId, from, to), (row, index) -> new SalesTotals(
                row.getBigDecimal(1), row.getBigDecimal(2), row.getInt(3), row.getInt(4)));
    }

    /**
     * GROUP BY metric_date và trả các ngày tăng dần để dựng biểu đồ doanh số theo ngày.
     */
    @Override
    public List<DailySalesRow> aggregateDaily(UUID eventId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT metric_date, SUM(revenue), SUM(refunded_amount), SUM(tickets_sold), SUM(tickets_refunded)
                """ + FILTER + " GROUP BY metric_date ORDER BY metric_date", params(eventId, from, to),
                (row, index) -> new DailySalesRow(row.getDate(1).toLocalDate(), row.getBigDecimal(2),
                        row.getBigDecimal(3), row.getInt(4), row.getInt(5)));
    }

    /**
     * GROUP BY ticket_type_id, chọn tên snapshot nhất quán bằng MAX và map UUID CHAR(36) về java.util.UUID.
     */
    @Override
    public List<TicketTypeSalesRow> aggregateByTicketType(UUID eventId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT ticket_type_id, MAX(ticket_type_name), SUM(revenue), SUM(refunded_amount),
                    SUM(tickets_sold), SUM(tickets_refunded)
                """ + FILTER + " GROUP BY ticket_type_id ORDER BY ticket_type_id", params(eventId, from, to),
                (row, index) -> new TicketTypeSalesRow(UUID.fromString(row.getString(1)), row.getString(2),
                        row.getBigDecimal(3), row.getBigDecimal(4), row.getInt(5), row.getInt(6)));
    }

    /**
     * Tạo named parameters dùng chung cho ba truy vấn. from/to được khai báo SQL DATE và có thể null để bỏ cận
     * lọc.
     */
    private MapSqlParameterSource params(UUID eventId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource().addValue("eventId", eventId.toString())
                .addValue("from", from, Types.DATE).addValue("to", to, Types.DATE);
    }
}
