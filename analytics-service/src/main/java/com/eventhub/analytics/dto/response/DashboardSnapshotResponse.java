package com.eventhub.analytics.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/dashboard và được đẩy qua WebSocket.
 * totalRevenue/totalTicketsSold là tổng gộp toàn sự kiện, phần hoàn hiển thị riêng trong báo cáo sales.
 *
 * @param eventId UUID sự kiện bán vé mà dữ liệu thuộc về.
 * @param totalRevenue Tổng doanh thu gộp trong phạm vi DTO, dashboard dùng toàn sự kiện.
 * @param totalTicketsSold Tổng số vé đã bán gộp trong phạm vi DTO.
 * @param currentAttendance Số vé đã có bằng chứng vào cổng và chưa có bằng chứng ra cổng.
 * @param totalCheckedIn Tổng số vé đã có bằng chứng check-in, mỗi vé đóng góp một lần.
 * @param updatedAt Mốc cập nhật dữ liệu, có thể null trong response khi chưa có snapshot.
 */
public record DashboardSnapshotResponse(
        UUID eventId,
        BigDecimal totalRevenue,
        int totalTicketsSold,
        int currentAttendance,
        int totalCheckedIn,
        Instant updatedAt
) {}
