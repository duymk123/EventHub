package com.eventhub.analytics.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/attendance.
 *
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param currentAttendance Số vé đã có bằng chứng vào cổng và chưa có bằng chứng ra cổng.
 * @param updatedAt Mốc cập nhật dữ liệu, có thể null trong response khi chưa có snapshot.
 * @param totalCheckedIn Tổng số vé đã có bằng chứng check-in, mỗi vé đóng góp một lần.
 */
public record AttendanceResponse(
        UUID eventId,
        int currentAttendance,
        Instant updatedAt,
        int totalCheckedIn
) {}
