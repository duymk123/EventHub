package com.eventhub.analytics.entity;

import com.eventhub.analytics.common.AttendanceAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Dấu vết từng message quét vé đã được áp dụng vào projection. Luồng hiện tại ghi id=messageId để lần replay
 * cùng message được nhận diện ổn định.
 */
@Entity
@Table(name = "attendance_logs", indexes = {
        @Index(name = "idx_att", columnList = "event_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLog {

    /**
     * UUID messageId nguồn mà projection dùng làm ID của log quét.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    /**
     * UUID vé, dùng làm Kafka key của sự kiện quét.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ticket_id", nullable = false, length = 36)
    private UUID ticketId;

    /**
     * UUID sự kiện bán vé mà dữ liệu thuộc về.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;

    /**
     * UUID cổng hoặc thiết bị gắn với lần quét.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "gate_id", length = 36)
    private UUID gateId;

    /**
     * Loại thao tác CHECK_IN hoặc CHECK_OUT của log.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, length = 10)
    private AttendanceAction action;

    /**
     * Thời điểm nghiệp vụ gốc theo UTC.
     */
    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant occurredAt;
}
