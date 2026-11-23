package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakSession;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.response.SessionResponse;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.service.SessionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionServiceImpl implements SessionService {

    private final KeycloakUserClient userClient;

    public SessionServiceImpl(KeycloakUserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public List<SessionResponse> mySessions(String keycloakSub) {
        return userClient.sessions(UUID.fromString(keycloakSub)).stream().map(SessionServiceImpl::toResponse).toList();
    }

    @Override
    public void revokeMySession(String keycloakSub, String sessionId) {
        UUID userId = UUID.fromString(keycloakSub);
        // Chỉ cho thu hồi phiên thuộc chính mình: sessionId là id Keycloak tự sinh, không đoán được, nhưng vẫn phải
        // kiểm tra quyền sở hữu để người dùng không thu hồi được phiên của người khác.
        boolean owned = userClient.sessions(userId).stream().anyMatch(session -> session.id().equals(sessionId));
        if (!owned) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Session not found");
        }
        userClient.revokeSession(sessionId);
    }

    private static SessionResponse toResponse(KeycloakSession session) {
        return new SessionResponse(session.id(), session.ipAddress(), toInstant(session.start()),
                toInstant(session.lastAccess()));
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}
