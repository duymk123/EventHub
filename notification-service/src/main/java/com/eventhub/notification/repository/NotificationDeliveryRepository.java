package com.eventhub.notification.repository;

import com.eventhub.notification.messaging.InvalidEventException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Các thao tác SQL phục vụ batch và giao nhận thông báo. Phương thức ghi được caller bọc trong transaction ngắn
 * bằng @Transactional hoặc TransactionTemplate.
 */
@Repository
public class NotificationDeliveryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public NotificationDeliveryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Tạo batch nếu thiếu rồi SELECT FOR UPDATE theo messageId để tuần tự hóa các lần prepare. So fingerprint
     * và báo lỗi nếu messageId cũ đi kèm dữ liệu nguồn khác, caller cần đang ở trong transaction.
     */
    public void lockBatch(UUID id, String fingerprint, Instant now) {
        jdbc.update("""
                INSERT INTO notification_batches(id, target_fingerprint, expected_count, status, created_at)
                VALUES (?, ?, 2, 'PENDING', ?) ON DUPLICATE KEY UPDATE id = id
                """, id.toString(), fingerprint, Timestamp.from(now));
        // Khóa nằm trong transaction prepare của caller, các worker cùng message phải chờ lượt.
        String existing = jdbc.queryForObject(
                "SELECT target_fingerprint FROM notification_batches WHERE id = ? FOR UPDATE",
                String.class, id.toString());
        if (!fingerprint.equals(existing)) {
            throw new InvalidEventException("Message ID reused with a different payload");
        }
    }

    /**
     * Lấy notificationId của một message nguồn theo thứ tự ổn định. Danh sách này được dùng lại ở các lần
     * delivery/retry.
     */
    public List<UUID> targets(UUID messageId) {
        return jdbc.query("SELECT id FROM notifications WHERE source_message_id = ? ORDER BY id",
                (row, index) -> UUID.fromString(row.getString(1)), messageId.toString());
    }

    /**
     * INSERT snapshot của một target với dedupKey gồm messageId, channel và người nhận. IN_APP được lưu SENT
     * ngay, EMAIL bắt đầu PENDING, ID của dòng notification được sinh riêng.
     */
    public void create(UUID messageId, UUID userId, String email, String channel, String code,
                       String title, String content, Map<String, Object> payload, Instant now) {
        boolean inApp = "IN_APP".equals(channel);
        jdbc.update("""
                INSERT INTO notifications(id, dedup_key, source_message_id, recipient_user_id, recipient_email,
                    channel, template_code, title, content, payload, status, attempts, sent_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, UUID.randomUUID().toString(), messageId + ":" + channel + ":" + (inApp ? userId : email),
                messageId.toString(), inApp ? userId.toString() : null, inApp ? null : email,
                /**
                 * Chuyển model đã render thành JSON snapshot lưu DB. Lỗi serialize được đổi thành thông điệp
                 * chung.
                 */
                channel, code, title, content, json(payload), inApp ? "SENT" : "PENDING",
                inApp ? Timestamp.from(now) : null, Timestamp.from(now));
    }

    /**
     * Conditional UPDATE nhận quyền gửi EMAIL khi target PENDING/FAILED và lease trống hoặc hết hạn. Tăng
     * attempts lúc claim, trả DeliveryWork khi lấy được quyền hoặc Optional.empty nếu target đã SENT/đang được
     * giữ.
     */
    public Optional<DeliveryWork> claim(UUID id, UUID token, Instant now, Instant until) {
        int changed = jdbc.update("""
                UPDATE notifications SET lease_token = ?, lease_until = ?, attempts = attempts + 1
                WHERE id = ? AND channel = 'EMAIL' AND status IN ('PENDING', 'FAILED')
                  AND (lease_until IS NULL OR lease_until <= ?)
                """, token.toString(), Timestamp.from(until), id.toString(), Timestamp.from(now));
        // UPDATE chỉ thành công khi điều kiện trạng thái và lease vẫn đúng tại thời điểm DB ghi.
        if (changed == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT recipient_email, title, content, payload FROM notifications WHERE id = ? AND lease_token = ?
                """, (row, index) -> new DeliveryWork(row.getString(1), row.getString(2), row.getString(3),
                // Khôi phục Map snapshot đã lưu để EmailSender dùng lại ở lần gửi/retry.
                payload(row.getString(4))), id.toString(), token.toString()));
    }

    /**
     * Đặt trạng thái SENT, thời gian gửi và xóa lease khi id/token vẫn khớp. Giá trị false báo quyền xử lý đã
     * chuyển sang token khác.
     */
    public boolean markSent(UUID id, UUID token, Instant now) {
        return jdbc.update("""
                UPDATE notifications SET status = 'SENT', sent_at = ?, last_error = NULL,
                    lease_token = NULL, lease_until = NULL WHERE id = ? AND lease_token = ?
                """, Timestamp.from(now), id.toString(), token.toString()) == 1;
    }

    /**
     * Gia hạn lease cho đúng id/token đang giữ, dùng bởi heartbeat khi gửi email kéo dài.
     */
    public boolean renew(UUID id, UUID token, Instant until) {
        return jdbc.update("UPDATE notifications SET lease_until = ? WHERE id = ? AND lease_token = ?",
                Timestamp.from(until), id.toString(), token.toString()) == 1;
    }

    /**
     * Ghi FAILED với nội dung lỗi đã lọc và nhả lease của worker hiện tại để lần retry có thể claim tiếp.
     */
    public void markFailed(UUID id, UUID token) {
        jdbc.update("""
                UPDATE notifications SET status = 'FAILED', last_error = 'Email delivery failed',
                    lease_token = NULL, lease_until = NULL WHERE id = ? AND lease_token = ?
                """, id.toString(), token.toString());
    }

    /**
     * Giữ khóa batch, đếm target chưa SENT và so tổng target với expectedCount. Khi đủ điều kiện mới ghi
     * processed_events và COMPLETE cùng transaction của caller.
     */
    public boolean complete(UUID messageId, Instant now) {
        jdbc.queryForObject("SELECT id FROM notification_batches WHERE id = ? FOR UPDATE",
                String.class, messageId.toString());
        // Kiểm tra cả số target còn thiếu và tổng target để batch chỉ hoàn tất khi đã tạo đủ.
        Long remaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE source_message_id = ? AND status <> 'SENT'
                """, Long.class, messageId.toString());
        Integer expected = jdbc.queryForObject(
                "SELECT expected_count FROM notification_batches WHERE id = ?", Integer.class, messageId.toString());
        if (remaining == null || remaining != 0 || targets(messageId).size() != expected) {
            return false;
        }
        // Dấu hoàn thành được ghi sau khi mọi EMAIL/IN_APP đã SENT, cùng transaction với COMPLETE.
        jdbc.update("INSERT INTO processed_events(id, processed_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE id = id",
                messageId.toString(), Timestamp.from(now));
        jdbc.update("UPDATE notification_batches SET status = 'COMPLETE' WHERE id = ?", messageId.toString());
        return true;
    }

    /**
     * Chuyển model đã render thành JSON snapshot lưu DB. Lỗi serialize được đổi thành thông điệp chung.
     */
    private String json(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize notification snapshot");
        }
    }

    /**
     * Khôi phục Map snapshot đã lưu để EmailSender dùng lại ở lần gửi/retry.
     */
    private Map<String, Object> payload(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (Exception error) {
            throw new IllegalStateException("Invalid notification snapshot");
        }
    }

    /**
     * Dữ liệu bất biến lấy sau khi claim lease, đủ thông tin để gọi SMTP ở bên ngoài transaction DB.
     * Dữ liệu được gom để truyền qua các bước xử lý.
     *
     * @param recipient Địa chỉ email dùng cho lần gửi đã claim.
     * @param subject Tiêu đề email đã render trong snapshot.
     * @param content Nội dung đã render và lưu cho lần gửi này.
     * @param payload Snapshot model phục vụ giao nhận, gồm dữ liệu QR khi có vé.
     */
    public record DeliveryWork(String recipient, String subject, String content, Map<String, Object> payload) {}
}
