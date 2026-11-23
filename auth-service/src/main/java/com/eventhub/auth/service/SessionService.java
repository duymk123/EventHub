package com.eventhub.auth.service;

import com.eventhub.auth.dto.response.SessionResponse;

import java.util.List;

/**
 * Phiên đăng nhập của người đang đăng nhập (Keycloak là nguồn sự thật). Access token đã cấp vẫn dùng được tới khi
 * hết hạn 5 phút sau khi thu hồi phiên.
 */
public interface SessionService {

    List<SessionResponse> mySessions(String keycloakSub);

    /** Thu hồi một phiên của chính mình; phiên của người khác → USER_NOT_FOUND. */
    void revokeMySession(String keycloakSub, String sessionId);
}
