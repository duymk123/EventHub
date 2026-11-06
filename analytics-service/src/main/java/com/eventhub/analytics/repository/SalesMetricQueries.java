package com.eventhub.analytics.repository;

import com.eventhub.analytics.repository.SalesMetricRepository.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fragment repository dành cho truy vấn tổng, theo ngày và theo hạng. SalesMetricQueriesImpl map từng cột JDBC
 * vào các record kết quả Java.
 */
public interface SalesMetricQueries {
    /**
     * Lấy tổng gộp/hoàn của một event trong khoảng from/to, cận null được bỏ qua.
     */
    SalesTotals sumTotals(UUID eventId, LocalDate from, LocalDate to);
    /**
     * Lấy các dòng thống kê theo ngày trong phạm vi lọc.
     */
    List<DailySalesRow> aggregateDaily(UUID eventId, LocalDate from, LocalDate to);
    /**
     * Lấy các dòng thống kê theo hạng vé trong phạm vi lọc.
     */
    List<TicketTypeSalesRow> aggregateByTicketType(UUID eventId, LocalDate from, LocalDate to);
}
