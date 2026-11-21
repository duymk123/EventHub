package com.eventhub.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * User sessions response.
 * Contains list of active sessions with device info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionsResponse {
    private List<SessionInfo> sessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private String id;
        private Instant startedAt;
        private Instant lastAccessedAt;
        private String ipAddress;
        private String userAgent;
        private String device;
    }
}