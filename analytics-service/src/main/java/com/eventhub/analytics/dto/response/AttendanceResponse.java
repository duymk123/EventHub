package com.eventhub.analytics.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Phản hồi trả về bởi GET /analytics/events/{id}/attendance.
 */
public record AttendanceResponse(
        UUID eventId,
        int currentAttendance,
        Instant updatedAt,
        int totalCheckedIn
) {}
