package com.eventhub.ticket.entity;

import com.eventhub.ticket.common.TicketTypeName;
import com.eventhub.ticket.common.TicketTypeStatus;

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
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 100)
    private TicketTypeName name;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;
    /** C12: số hàng ghế organizer nhập; 0 = zone chưa mở bán (C08). */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    /** C12: số ghế mỗi hàng, các hàng bằng nhau. */
    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    /** = rowCount × seatsPerRow (CHECK trong DDL). Tồn kho thật là bảng seats theo trạng thái. */
    @Column(name = "total_quota", nullable = false)
    private int totalQuota;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TicketTypeStatus status = TicketTypeStatus.ACTIVE;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
