package com.eventhub.auth.dto.response;

import java.time.Instant;

/**
 * Một phiên đăng nhập còn hiệu lực.
 *
 * @param id dùng cho DELETE /api/v1/auth/me/sessions/{sessionId}.
 * @param startedAt thời điểm phiên bắt đầu; Keycloak trả epoch millis.
 * @param lastAccessAt lần truy cập gần nhất của phiên.
 */
public record SessionResponse(String id, String ipAddress, Instant startedAt, Instant lastAccessAt) {
}
