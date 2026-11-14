package com.eventhub.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Bằng chứng check-in và check-out của một vé, khóa duy nhất theo ticketId. Giữ cả hai mốc thời gian để
 * projection xử lý được checkout đến trước checkin.
 */
@Entity
@Table(name = "attendance_state")
@Getter
@NoArgsConstructor
public class AttendanceState {
    /**
     * UUID bản ghi trạng thái, ticketId xác định duy nhất vé được theo dõi.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    /**
     * UUID vé, dùng làm Kafka key của sự kiện quét.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ticket_id", nullable = false, unique = true, length = 36)
    private UUID ticketId;

    /**
     * UUID sự kiện bán vé mà dữ liệu thuộc về.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;

    /**
     * Mốc check-in đã thấy, null khi chưa nhận bằng chứng.
     */
    @Column(name = "checked_in_at", columnDefinition = "TIMESTAMP")
    private Instant checkedInAt;

    /**
     * Mốc check-out đã thấy, null khi chưa nhận bằng chứng.
     */
    @Column(name = "checked_out_at", columnDefinition = "TIMESTAMP")
    private Instant checkedOutAt;
}
