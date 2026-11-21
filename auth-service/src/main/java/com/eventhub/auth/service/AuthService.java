package com.eventhub.auth.service;

import com.eventhub.auth.dto.request.*;
import com.eventhub.auth.dto.response.*;
import com.eventhub.auth.security.Actor;
import reactor.core.publisher.Mono;

/**
 * Auth Service interface for user-facing authentication operations.
 * All methods are reactive (Mono) for non-blocking Keycloak calls.
 */
public interface AuthService {
    /**
     * Register new user (CUSTOMER role).
     * Creates user in Keycloak with email, password, fullName.
     */
    Mono<UserResponse> register(RegisterRequest request);

    /**
     * Login with authorization code (PKCE flow).
     * Exchanges code for access/refresh tokens at Keycloak.
     */
    Mono<TokenResponse> login(LoginRequest request);

    /**
     * Refresh access token using refresh token.
     */
    Mono<TokenResponse> refresh(RefreshRequest request);

    /**
     * Logout (revoke refresh token at Keycloak).
     */
    Mono<Void> logout(RefreshRequest request);

    /**
     * Get current user profile from Keycloak.
     */
    Mono<UserResponse> getMe(Actor actor);

    /**
     * Change password (user knows current password).
     */
    Mono<Void> changePassword(Actor actor, ChangePasswordRequest request);

    /**
     * Forgot password - send reset email.
     */
    Mono<Void> forgotPassword(ForgotPasswordRequest request);

    /**
     * Reset password with token from email.
     */
    Mono<Void> resetPassword(ResetPasswordRequest request);

    /**
     * Verify email with token from email.
     */
    Mono<Void> verifyEmail(VerifyEmailRequest request);

    /**
     * Resend verification email.
     */
    Mono<Void> resendVerification(ResendVerificationRequest request);

    /**
     * Get current user's active sessions.
     */
    Mono<UserSessionsResponse> getMySessions(Actor actor);

    /**
     * Revoke a specific session of current user.
     */
    Mono<Void> revokeMySession(Actor actor, String sessionId);
}