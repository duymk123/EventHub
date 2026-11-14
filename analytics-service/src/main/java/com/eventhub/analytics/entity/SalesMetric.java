package com.eventhub.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một dòng tổng hợp cho tổ hợp eventId, ticketTypeId và metricDate. revenue/ticketsSold giữ phần gộp,
 * refundedAmount/ticketsRefunded giữ phần hoàn.
 */
@Entity
@Table(name = "sales_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_metric", columnNames = {"event_id", "ticket_type_id", "metric_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesMetric {

    /**
     * UUID dòng metric, dữ liệu được nhóm duy nhất theo event/type/ngày.
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
    @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;

    /**
     * UUID hạng vé, là khóa nhóm số liệu theo từng hạng.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ticket_type_id", nullable = false, length = 36)
    private UUID ticketTypeId;

    /**
     * Tên hạng vé được snapshot, nhận VIP, PREMIUM hoặc STANDARD.
     */
    @Column(name = "ticket_type_name", nullable = false, length = 100)
    private String ticketTypeName;

    /**
     * Ngày Việt Nam dùng làm khóa nhóm metric theo event và hạng vé.
     */
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    /**
     * Doanh thu gộp của dòng metric, được cộng từ order.paid.
     */
    @Builder.Default
    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    /**
     * Số vé đã bán gộp của dòng.
     */
    @Column(name = "tickets_sold", nullable = false)
    private int ticketsSold;

    /**
     * Tổng tiền hoàn của dòng metric, được cộng từ order.refunded.
     */
    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /**
     * Số vé đã hoàn của dòng.
     */
    @Column(name = "tickets_refunded", nullable = false)
    private int ticketsRefunded;

    // Mã projection cung cấp thời gian nghiệp vụ; không thay thế bằng đồng hồ audit.
    /**
     * Mốc thời gian nghiệp vụ lớn nhất đã cập nhật dòng metric.
     */
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;
}
