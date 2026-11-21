package com.eventhub.auth.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * User sessions response from Keycloak Admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KCUserSessionsResponse {
    private List<KCSessionRepresentation> sessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KCSessionRepresentation {
        private String id;
        private Long start;
        private Long lastAccess;
        private String ipAddress;
        private String userAgent;
        private String device;
    }
}