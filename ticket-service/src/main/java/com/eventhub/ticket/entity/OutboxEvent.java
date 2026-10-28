package com.eventhub.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_pending", columnList = "status, created_at"))
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, updatable = false, length = 36)
    private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "aggregate_id", nullable = false, length = 36)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json")
    private String payload;
    @Column(nullable = false, length = 16)
    private String status = "PENDING";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
}
