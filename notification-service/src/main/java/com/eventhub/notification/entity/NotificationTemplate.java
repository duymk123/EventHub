package com.eventhub.notification.entity;

import com.eventhub.notification.common.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Mẫu nội dung dùng lại, phân biệt bằng code và channel. NotificationBatchService đọc mẫu và lưu kết quả render
 * thành snapshot trên từng Notification.
 */
@Entity
@Table(name = "notification_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template", columnNames = {"code", "channel"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    /**
     * UUID bản ghi mẫu, cặp code/channel là khóa nghiệp vụ duy nhất.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    /**
     * Mã template nghiệp vụ dùng để chọn mẫu.
     */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /**
     * Kênh EMAIL hoặc IN_APP của mẫu hoặc thông báo.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    /**
     * Mẫu tiêu đề được render khi chuẩn bị notification.
     */
    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    /**
     * Mẫu Mustache chứa biến của nội dung thông báo.
     */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    /**
     * Mốc lưu template gần nhất, Hibernate gán khi dùng JPA, SQL gán rõ khi UPSERT.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;
}
