package com.eventhub.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng tổng hợp cho một event, phục vụ REST và push dashboard. Các cập nhật SQL cộng vào đúng cột để sales
 * và attendance cùng bảo toàn dữ liệu.
 */
@Entity
@Table(name = "dashboard_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSnapshot {

    /**
     * UUID dòng snapshot, mỗi eventId có một snapshot duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    /**
     * UUID sự kiện bán vé mà dữ liệu thuộc về.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private UUID eventId;

    /**
     * Tổng doanh thu gộp toàn sự kiện đang lưu trong snapshot.
     */
    @Builder.Default
    @Column(name = "total_revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    /**
     * Tổng số vé đã bán gộp trong phạm vi DTO.
     */
    @Column(name = "total_tickets_sold", nullable = false)
    private int totalTicketsSold;

    /**
     * Số vé đã có bằng chứng vào cổng và chưa có bằng chứng ra cổng.
     */
    @Column(name = "current_attendance", nullable = false)
    private int currentAttendance;

    /**
     * Tổng số vé đã có bằng chứng check-in, mỗi vé đóng góp một lần.
     */
    @Column(name = "total_checked_in", nullable = false)
    private int totalCheckedIn;

    /**
     * Mốc thời gian nghiệp vụ lớn nhất đã cập nhật snapshot.
     */
    @Column(name = "last_updated", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant lastUpdated;
}
