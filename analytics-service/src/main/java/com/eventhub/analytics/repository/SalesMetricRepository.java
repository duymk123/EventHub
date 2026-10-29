package com.eventhub.analytics.repository;

import com.eventhub.analytics.entity.SalesMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalesMetricRepository extends JpaRepository<SalesMetric, UUID> {

    /**
     * UPSERT nguyên tử cho doanh số: revenue +=, tickets_sold +=.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sales_metrics (id, event_id, ticket_type_id, ticket_type_name, metric_date,
                                       revenue, tickets_sold, refunded_amount, tickets_refunded, updated_at)
            VALUES (:id, :eventId, :ticketTypeId, :ticketTypeName, :metricDate,
                    :revenue, :qty, 0, 0, :at)
            ON DUPLICATE KEY UPDATE
                revenue = revenue + VALUES(revenue),
                tickets_sold = tickets_sold + VALUES(tickets_sold),
                updated_at = GREATEST(updated_at, VALUES(updated_at))
            """, nativeQuery = true)
    int upsertSale(@Param("id") UUID id,
                   @Param("eventId") UUID eventId,
                   @Param("ticketTypeId") UUID ticketTypeId,
                   @Param("ticketTypeName") String ticketTypeName,
                   @Param("metricDate") LocalDate metricDate,
                   @Param("revenue") BigDecimal revenue,
                   @Param("qty") int qty,
                   @Param("at") Instant at);

    /**
     * UPSERT nguyên tử cho hoàn tiền: chỉ tăng refunded_amount và tickets_refunded.
     * KHÔNG trừ revenue của ngày trước đó.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sales_metrics (id, event_id, ticket_type_id, ticket_type_name, metric_date,
                                       revenue, tickets_sold, refunded_amount, tickets_refunded, updated_at)
            VALUES (:id, :eventId, :ticketTypeId, :ticketTypeName, :metricDate,
                    0, 0, :amount, :qty, :at)
            ON DUPLICATE KEY UPDATE
                refunded_amount = refunded_amount + VALUES(refunded_amount),
                tickets_refunded = tickets_refunded + VALUES(tickets_refunded),
                updated_at = GREATEST(updated_at, VALUES(updated_at))
            """, nativeQuery = true)
    int upsertRefund(@Param("id") UUID id,
                     @Param("eventId") UUID eventId,
                     @Param("ticketTypeId") UUID ticketTypeId,
                     @Param("ticketTypeName") String ticketTypeName,
                     @Param("metricDate") LocalDate metricDate,
                     @Param("amount") BigDecimal amount,
                     @Param("qty") int qty,
                     @Param("at") Instant at);

    /**
     * Tổng hợp toàn bộ cho một sự kiện trong khoảng ngày (tùy chọn).
     * Nếu thiếu cận trên/dưới thì không giới hạn (NULL from/to).
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(revenue), 0) AS totalRevenue,
                COALESCE(SUM(refunded_amount), 0) AS totalRefunded,
                COALESCE(SUM(tickets_sold), 0) AS totalTicketsSold,
                COALESCE(SUM(tickets_refunded), 0) AS totalTicketsRefunded
            FROM sales_metrics
            WHERE event_id = :eventId
              AND (:from IS NULL OR metric_date >= :from)
              AND (:to IS NULL OR metric_date <= :to)
            """, nativeQuery = true)
    SalesTotals sumTotals(@Param("eventId") UUID eventId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /**
     * Phân tích theo ngày, sắp xếp theo ngày tăng dần.
     */
    @Query(value = """
            SELECT
                metric_date AS date,
                COALESCE(SUM(revenue), 0) AS revenue,
                COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                COALESCE(SUM(tickets_sold), 0) AS ticketsSold,
                COALESCE(SUM(tickets_refunded), 0) AS ticketsRefunded
            FROM sales_metrics
            WHERE event_id = :eventId
              AND (:from IS NULL OR metric_date >= :from)
              AND (:to IS NULL OR metric_date <= :to)
            GROUP BY metric_date
            ORDER BY metric_date ASC
            """, nativeQuery = true)
    List<DailySalesRow> aggregateDaily(@Param("eventId") UUID eventId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * Phân tích theo loại vé. Nhóm theo ID loại; tên được chọn nhất quán.
     */
    @Query(value = """
            SELECT
                ticket_type_id AS ticketTypeId,
                MAX(ticket_type_name) AS ticketTypeName,
                COALESCE(SUM(revenue), 0) AS revenue,
                COALESCE(SUM(refunded_amount), 0) AS refundedAmount,
                COALESCE(SUM(tickets_sold), 0) AS ticketsSold,
                COALESCE(SUM(tickets_refunded), 0) AS ticketsRefunded
            FROM sales_metrics
            WHERE event_id = :eventId
              AND (:from IS NULL OR metric_date >= :from)
              AND (:to IS NULL OR metric_date <= :to)
            GROUP BY ticket_type_id
            ORDER BY ticket_type_id ASC
            """, nativeQuery = true)
    List<TicketTypeSalesRow> aggregateByTicketType(@Param("eventId") UUID eventId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);

    record SalesTotals(BigDecimal totalRevenue, BigDecimal totalRefunded,
                       int totalTicketsSold, int totalTicketsRefunded) {}

    record DailySalesRow(LocalDate date, BigDecimal revenue, BigDecimal refundedAmount,
                         int ticketsSold, int ticketsRefunded) {}

    record TicketTypeSalesRow(UUID ticketTypeId, String ticketTypeName,
                              BigDecimal revenue, BigDecimal refundedAmount,
                              int ticketsSold, int ticketsRefunded) {}
}
