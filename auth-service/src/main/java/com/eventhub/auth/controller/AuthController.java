package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.*;
import com.eventhub.auth.dto.response.*;
import com.eventhub.auth.security.Actor;
import com.eventhub.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Auth Controller - Public and user self-service endpoints.
 * All endpoints under /api/v1/auth.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .map(user -> ResponseEntity.status(201).body(user))
                .block();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok)
                .block();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request)
                .map(ResponseEntity::ok)
                .block();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request).block();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        Actor actor = extractActor(jwt);
        return authService.getMe(actor)
                .map(ResponseEntity::ok)
                .block();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        Actor actor = extractActor(jwt);
        authService.changePassword(actor, request).block();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request).block();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request).block();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request).block();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request).block();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/sessions")
    public ResponseEntity<UserSessionsResponse> getMySessions(@AuthenticationPrincipal Jwt jwt) {
        Actor actor = extractActor(jwt);
        return authService.getMySessions(actor)
                .map(ResponseEntity::ok)
                .block();
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> revokeMySession(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable String sessionId) {
        Actor actor = extractActor(jwt);
        authService.revokeMySession(actor, sessionId).block();
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract Actor from JWT.
     * In production, this should use IdentityMapper bean.
     * Kept here for simplicity until IdentityMapper is properly injected.
     */
    private Actor extractActor(Jwt jwt) {
        String sub = jwt.getSubject();
        var realmAccess = jwt.getClaim("realm_access");
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) ((java.util.Map<String, Object>) realmAccess).get("roles");
        com.eventhub.auth.security.BusinessRole role = roles.stream()
                .filter(r -> r.equals("CUSTOMER") || r.equals("ORGANIZER") || r.equals("ADMIN"))
                .map(com.eventhub.auth.security.BusinessRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No business role in token"));
        return new Actor(UUID.fromString(sub), role);
    }
}