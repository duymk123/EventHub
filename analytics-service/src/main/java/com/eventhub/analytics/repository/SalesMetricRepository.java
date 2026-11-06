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

/**
 * Repository của SalesMetric kết hợp JPA và fragment SalesMetricQueries. Các phương thức upsert cộng số
 * bán/hoàn theo unique event/type/date trong transaction projection.
 */
@Repository
public interface SalesMetricRepository extends JpaRepository<SalesMetric, UUID>, SalesMetricQueries {

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
     * Dữ liệu được gom để truyền qua các bước xử lý.
     *
     * @param totalRevenue Tổng doanh thu gộp trong phạm vi DTO, dashboard dùng toàn sự kiện.
     * @param totalRefunded Tổng tiền hoàn trong khoảng báo cáo.
     * @param totalTicketsSold Tổng số vé đã bán gộp trong phạm vi DTO.
     * @param totalTicketsRefunded Tổng số vé hoàn trong khoảng báo cáo.
     */
    record SalesTotals(BigDecimal totalRevenue, BigDecimal totalRefunded,
                       int totalTicketsSold, int totalTicketsRefunded) {}

    /**
     * Dữ liệu được gom để truyền qua các bước xử lý.
     *
     * @param date Ngày báo cáo Việt Nam theo định dạng yyyy-MM-dd.
     * @param revenue Doanh thu gộp của dòng báo cáo.
     * @param refundedAmount Tiền hoàn được phân bổ cho hạng vé hoặc tổng tiền hoàn của dòng báo cáo.
     * @param ticketsSold Số vé đã bán gộp của dòng.
     * @param ticketsRefunded Số vé đã hoàn của dòng.
     */
    record DailySalesRow(LocalDate date, BigDecimal revenue, BigDecimal refundedAmount,
                         int ticketsSold, int ticketsRefunded) {}

    /**
     * Dữ liệu được gom để truyền qua các bước xử lý.
     *
     * @param ticketTypeId UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
     * @param ticketTypeName Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
     * @param revenue Doanh thu gộp của dòng báo cáo.
     * @param refundedAmount Tiền hoàn được phân bổ cho hạng vé hoặc tổng tiền hoàn của dòng báo cáo.
     * @param ticketsSold Số vé đã bán gộp của dòng.
     * @param ticketsRefunded Số vé đã hoàn của dòng.
     */
    record TicketTypeSalesRow(UUID ticketTypeId, String ticketTypeName,
                              BigDecimal revenue, BigDecimal refundedAmount,
                              int ticketsSold, int ticketsRefunded) {}
}
