package com.eventhub.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Lô xử lý ứng với một message Kafka. expectedCount dùng kiểm tra đủ hai target EMAIL/IN_APP, fingerprint phát
 * hiện cùng messageId nhưng dữ liệu nguồn đã thay đổi.
 */
@Entity
@Table(name = "notification_batches")
@Getter
@NoArgsConstructor
public class NotificationBatch {
    /**
     * UUID messageId nguồn, cũng là khóa chính của batch.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    /**
     * SHA-256 event đã parse, dùng phát hiện cùng messageId nhưng payload khác.
     */
    @Column(name = "target_fingerprint", nullable = false, length = 64)
    private String targetFingerprint;

    /**
     * Tổng target dự kiến của batch, hiện gồm một EMAIL và một IN_APP.
     */
    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    /**
     * PENDING khi đang xử lý, COMPLETE sau khi đủ target SENT.
     */
    @Column(nullable = false, length = 10)
    private String status;

    /**
     * Thời điểm tạo bản ghi UTC.
     */
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;
}
