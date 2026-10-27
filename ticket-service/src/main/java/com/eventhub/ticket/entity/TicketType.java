package com.eventhub.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_type_name", columnNames = {"event_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class TicketType {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 100)
    private TicketTypeName name;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;
    @Column(name = "total_quota", nullable = false)
    private int totalQuota;
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;
    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TicketTypeStatus status = TicketTypeStatus.ACTIVE;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
