package com.eventhub.analytics.repository;

import com.eventhub.analytics.entity.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, UUID> {

    Optional<DashboardSnapshot> findByEventId(UUID eventId);

    /**
     * UPSERT nguyên tử cho phần doanh số: total_revenue +=, total_tickets_sold +=.
     * Giữ nguyên các cột điểm danh.
     */
    @Modifying
    @Query(value = """
            INSERT INTO dashboard_snapshot (id, event_id, total_revenue, total_tickets_sold,
                                            current_attendance, total_checked_in, last_updated)
            VALUES (:id, :eventId, :amount, :qty, 0, 0, :at)
            ON DUPLICATE KEY UPDATE
                total_revenue = total_revenue + VALUES(total_revenue),
                total_tickets_sold = total_tickets_sold + VALUES(total_tickets_sold),
                last_updated = GREATEST(last_updated, VALUES(last_updated))
            """, nativeQuery = true)
    int addSales(@Param("id") UUID id,
                 @Param("eventId") UUID eventId,
                 @Param("amount") BigDecimal amount,
                 @Param("qty") int qty,
                 @Param("at") Instant at);

    /**
     * Điều chỉnh hoàn tiền nguyên tử trên snapshot.
     * Công thức chờ C09; không giới hạn điều chỉnh về 0.
     */
    @Modifying
    @Query(value = """
            INSERT INTO dashboard_snapshot (id, event_id, total_revenue, total_tickets_sold,
                                            current_attendance, total_checked_in, last_updated)
            VALUES (:id, :eventId, 0, 0, 0, 0, :at)
            ON DUPLICATE KEY UPDATE
                total_revenue = total_revenue - VALUES(total_revenue),
                total_tickets_sold = total_tickets_sold - VALUES(total_tickets_sold),
                last_updated = GREATEST(last_updated, VALUES(last_updated))
            """, nativeQuery = true)
    int applyRefund(@Param("id") UUID id,
                    @Param("eventId") UUID eventId,
                    @Param("at") Instant at);

    /**
     * Cập nhật delta điểm danh nguyên tử: chỉ current_attendance và total_checked_in.
     * Giữ nguyên các cột doanh số.
     */
    @Modifying
    @Query(value = """
            INSERT INTO dashboard_snapshot (id, event_id, total_revenue, total_tickets_sold,
                                            current_attendance, total_checked_in, last_updated)
            VALUES (:id, :eventId, 0, 0, :currentDelta, :checkedInDelta, :at)
            ON DUPLICATE KEY UPDATE
                current_attendance = current_attendance + VALUES(current_attendance),
                total_checked_in = total_checked_in + VALUES(total_checked_in),
                last_updated = GREATEST(last_updated, VALUES(last_updated))
            """, nativeQuery = true)
    int addAttendanceDelta(@Param("id") UUID id,
                           @Param("eventId") UUID eventId,
                           @Param("currentDelta") int currentDelta,
                           @Param("checkedInDelta") int checkedInDelta,
                           @Param("at") Instant at);
}
