package com.eventhub.analytics.repository;

import com.eventhub.analytics.messaging.DuplicateMessageException;
import com.eventhub.analytics.messaging.InvalidEventException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Các thao tác SQL atomic cho claim, khóa snapshot, state từng vé và attendance log.
 * AnalyticsProjectionServiceImpl cung cấp transaction bao quanh toàn bộ các thao tác của một event.
 */
@Repository
public class ProjectionRepository {
    private final JdbcTemplate jdbc;

    public ProjectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * INSERT processed_events bằng messageId trong transaction hiện tại. Chỉ DuplicateKeyException của thao tác
     * claim này được đổi thành DuplicateMessageException, các bước sau lỗi sẽ rollback cả claim.
     */
    public void claim(UUID messageId, Instant now) {
        try {
            jdbc.update("INSERT INTO processed_events(id, processed_at) VALUES (?, ?)",
                    messageId.toString(), Timestamp.from(now));
        // Chỉ unique conflict của INSERT claim được chuyển thành tín hiệu message đã xử lý.
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateMessageException();
        }
    }

    /**
     * Đảm bảo có một dòng cho event rồi khóa SELECT FOR UPDATE. Khóa giữ đến hết transaction của handler, tuần
     * tự hóa các cập nhật snapshot của cùng event.
     */
    public void lockSnapshot(UUID eventId, Instant at) {
        jdbc.update("""
                INSERT INTO dashboard_snapshot(id, event_id, total_revenue, total_tickets_sold,
                    current_attendance, total_checked_in, last_updated)
                VALUES (?, ?, 0, 0, 0, 0, ?) ON DUPLICATE KEY UPDATE id = id
                """, UUID.randomUUID().toString(), eventId.toString(), Timestamp.from(at));
        jdbc.queryForObject("SELECT id FROM dashboard_snapshot WHERE event_id = ? FOR UPDATE",
                String.class, eventId.toString());
    }

    /**
     * Cộng revenue và sold vào đúng hai cột tổng gộp, giữ các cột attendance. GREATEST giữ last_updated tiến về
     * phía trước khi message đến muộn.
     */
    public void addSalesSnapshot(UUID eventId, BigDecimal revenue, int sold, Instant at) {
        jdbc.update("""
                UPDATE dashboard_snapshot SET total_revenue = total_revenue + ?,
                    total_tickets_sold = total_tickets_sold + ?, last_updated = GREATEST(last_updated, ?)
                WHERE event_id = ?
                """, revenue, sold, Timestamp.from(at), eventId.toString());
    }

    /**
     * Đảm bảo có state duy nhất cho ticketId rồi khóa đọc hai bằng chứng đã thấy. Kiểm tra eventId nhất quán
     * trước khi trả AttendanceEvidence cho handler.
     */
    public AttendanceEvidence lockTicket(UUID ticketId, UUID eventId) {
        jdbc.update("""
                INSERT INTO attendance_state(id, ticket_id, event_id) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, UUID.randomUUID().toString(), ticketId.toString(), eventId.toString());
        return jdbc.queryForObject("""
                SELECT event_id, checked_in_at, checked_out_at FROM attendance_state WHERE ticket_id = ? FOR UPDATE
                """, (row, index) -> {
                    if (!eventId.toString().equals(row.getString(1))) {
                        throw new InvalidEventException("Ticket event identity conflict");
                    }
                    return new AttendanceEvidence(row.getTimestamp(2) != null, row.getTimestamp(3) != null);
                }, ticketId.toString());
    }

    /**
     * Giữ mốc vào/ra đầu tiên bằng COALESCE, ghi log id=messageId rồi cộng delta attendance vào snapshot. Tên
     * cột động được chọn từ boolean nội bộ, dữ liệu còn lại dùng JDBC parameters.
     */
    public void recordAttendance(UUID messageId, UUID eventId, UUID ticketId, UUID gateId,
                                 boolean checkIn, Instant at, int currentDelta, int checkedInDelta) {
        // Tên cột do boolean nội bộ chọn, các giá trị message vẫn được bind bằng dấu hỏi JDBC.
        String column = checkIn ? "checked_in_at" : "checked_out_at";
        jdbc.update("UPDATE attendance_state SET " + column + " = COALESCE(" + column + ", ?) WHERE ticket_id = ?",
                Timestamp.from(at), ticketId.toString());
        jdbc.update("""
                INSERT INTO attendance_logs(id, ticket_id, event_id, gate_id, action, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, messageId.toString(), ticketId.toString(), eventId.toString(), gateId.toString(),
                checkIn ? "CHECK_IN" : "CHECK_OUT", Timestamp.from(at));
        jdbc.update("""
                UPDATE dashboard_snapshot SET current_attendance = current_attendance + ?,
                    total_checked_in = total_checked_in + ?, last_updated = GREATEST(last_updated, ?)
                WHERE event_id = ?
                """, currentDelta, checkedInDelta, Timestamp.from(at), eventId.toString());
    }

    /**
     * Hai bằng chứng đã lưu của một vé trước lần cập nhật.
     *
     * @param checkedIn Đã nhận bằng chứng check-in của vé.
     * @param checkedOut Đã nhận bằng chứng check-out của vé.
     */
    public record AttendanceEvidence(boolean checkedIn, boolean checkedOut) {
        /**
         * Đóng góp hiện diện của một vé bằng 1 khi đã có check-in và chưa có check-out, các trường hợp còn lại
         * bằng 0.
         */
        public int contribution() {
            return checkedIn && !checkedOut ? 1 : 0;
        }
    }
}
