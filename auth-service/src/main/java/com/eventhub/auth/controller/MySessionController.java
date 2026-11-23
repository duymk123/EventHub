package com.eventhub.auth.controller;

import com.eventhub.auth.dto.response.SessionResponse;
import com.eventhub.auth.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phiên đăng nhập của chính mình — flow 14 trong docs/architecture/auth-keycloak-flows.md.
 */
@RestController
@RequestMapping("/api/v1/auth/me/sessions")
public class MySessionController {

    private final SessionService sessionService;

    public MySessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** GET /api/v1/auth/me/sessions → các phiên còn hiệu lực của người đang đăng nhập. */
    @GetMapping
    public ResponseEntity<List<SessionResponse>> mySessions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(sessionService.mySessions(jwt.getSubject()));
    }

    /** DELETE /api/v1/auth/me/sessions/{sessionId} → 204; phiên không thuộc mình → 404. */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        sessionService.revokeMySession(jwt.getSubject(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
