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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ticket_type_id", nullable = false, length = 36)
    private UUID ticketTypeId;

    @Column(name = "ticket_type_name", nullable = false, length = 100)
    private String ticketTypeName;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Builder.Default
    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "tickets_sold", nullable = false)
    private int ticketsSold;

    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "tickets_refunded", nullable = false)
    private int ticketsRefunded;

    // Mã projection cung cấp thời gian nghiệp vụ; không thay thế bằng đồng hồ audit.
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;
}
