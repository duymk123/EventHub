package com.eventhub.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Claim duy nhất theo messageId của Analytics. Claim được INSERT trong cùng transaction với metric/log/snapshot
 * và rollback cùng dữ liệu khi projection lỗi.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    /**
     * messageId của Kafka do consumer cung cấp; không bao giờ tạo ID mới để tránh trùng lặp.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    /**
     * Thời điểm ghi dấu message trong DB của service.
     */
    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant processedAt;
}
