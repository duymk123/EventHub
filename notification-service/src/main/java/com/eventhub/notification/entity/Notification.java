package com.eventhub.notification.entity;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.common.NotificationStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một lần giao nội dung cho một người nhận qua một kênh. sourceMessageId nối về message Kafka, dedupKey chống
 * tạo lại cùng target và lease kiểm soát worker gửi EMAIL.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_inapp", columnList = "recipient_user_id, channel, read_at, created_at"),
        @Index(name = "idx_delivery_message", columnList = "source_message_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    /**
     * UUID của một target giao nhận, từng EMAIL và IN_APP có ID riêng.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    /**
     * Khóa duy nhất ghép messageId, channel và người nhận.
     */
    @Column(name = "dedup_key", nullable = false, unique = true, length = 400)
    private String dedupKey;

    /**
     * UUID message Kafka nguồn của notification, liên kết về batch.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_message_id", nullable = false, length = 36)
    private UUID sourceMessageId;

    /**
     * UUID người nhận IN_APP, truy vấn REST dùng trường này để lọc owner.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "recipient_user_id", length = 36)
    private UUID recipientUserId;

    /**
     * Địa chỉ người mua dùng cho target EMAIL.
     */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /**
     * Kênh EMAIL hoặc IN_APP của mẫu hoặc thông báo.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    /**
     * Mã template đã dùng để tạo snapshot thông báo.
     */
    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    /**
     * Tiêu đề đã render và lưu cho lần gửi này.
     */
    @Column(name = "title", length = 255)
    private String title;

    /**
     * Nội dung đã render và lưu cho lần gửi này.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * Snapshot model phục vụ giao nhận, gồm dữ liệu QR khi có vé.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSON")
    private Map<String, Object> payload;

    /**
     * Trạng thái giao nhận, EMAIL bắt đầu PENDING và IN_APP được chuẩn bị SENT.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * Số lần worker đã claim để bắt đầu gửi EMAIL.
     */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /**
     * UUID nhận diện lượt claim hiện tại, các lệnh ghi kết quả phải khớp token này.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "lease_token", length = 36)
    private UUID leaseToken;

    /**
     * Thời hạn worker đang giữ quyền xử lý target.
     */
    @Column(name = "lease_until", columnDefinition = "TIMESTAMP")
    private Instant leaseUntil;

    /**
     * Mô tả lỗi giao nhận đã được lọc dữ liệu nhạy cảm.
     */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /**
     * Thời điểm giao target thành công.
     */
    @Column(name = "sent_at", columnDefinition = "TIMESTAMP")
    private Instant sentAt;

    /**
     * Thời điểm người dùng đánh dấu IN_APP đã đọc, null khi chưa đọc.
     */
    @Column(name = "read_at", columnDefinition = "TIMESTAMP")
    private Instant readAt;

    /**
     * Thời điểm tạo bản ghi UTC.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;
}
