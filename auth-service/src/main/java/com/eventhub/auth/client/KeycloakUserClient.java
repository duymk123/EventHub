package com.eventhub.auth.client;

import com.eventhub.auth.client.dto.KCCreateUserCommand;
import com.eventhub.auth.client.dto.KCUserRepresentation;
import com.eventhub.auth.client.dto.KCUserSessionsResponse;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface for Keycloak Admin API operations (User management).
 * Handles: CRUD users, role mapping, sessions, password reset, email verification.
 * Uses Keycloak Admin Client (blocking) wrapped in Mono.
 */
public interface KeycloakUserClient {
    /**
     * Create a new user in Keycloak.
     * POST /admin/realms/{realm}/users
     */
    Mono<KCUserRepresentation> createUser(KCCreateUserCommand command);

    /**
     * Get user by ID.
     * GET /admin/realms/{realm}/users/{id}
     */
    Mono<KCUserRepresentation> getUser(String userId);

    /**
     * Get users with pagination and search.
     * GET /admin/realms/{realm}/users
     */
    Mono<List<KCUserRepresentation>> getUsers(int first, int max, String search);

    /**
     * Enable or disable user.
     * PUT /admin/realms/{realm}/users/{id}
     */
    Mono<Void> setEnabled(String userId, boolean enabled);

    /**
     * Assign a business role to user (replaces existing business roles).
     * Business roles: CUSTOMER, ORGANIZER, ADMIN.
     * PUT /admin/realms/{realm}/users/{id}/role-mappings/realm
     */
    Mono<Void> assignRole(String userId, String role);

    /**
     * Reset user password.
     * Actions: SEND_EMAIL (send reset link), SET_TEMPORARY_PASSWORD (admin sets temp password).
     * PUT /admin/realms/{realm}/users/{id}/reset-password or executeActionsEmail
     */
    Mono<Void> resetPassword(String userId, String action, String temporaryPassword);

    /**
     * Get user sessions.
     * GET /admin/realms/{realm}/users/{id}/sessions
     */
    Mono<KCUserSessionsResponse> getUserSessions(String userId);

    /**
     * Revoke all user sessions (logout all devices).
     * POST /admin/realms/{realm}/users/{id}/logout
     */
    Mono<Void> revokeAllSessions(String userId);

    /**
     * Revoke specific session.
     * DELETE /admin/realms/{realm}/sessions/{sessionId}
     */
    Mono<Void> revokeSession(String sessionId);

    /**
     * Send email verification link.
     * POST /admin/realms/{realm}/users/{id}/execute-actions-email
     */
    Mono<Void> sendVerifyEmail(String userId);

    /**
     * Send forgot password email (reset password action).
     * POST /admin/realms/{realm}/users/{id}/execute-actions-email
     */
    Mono<Void> sendForgotPasswordEmail(String userId);
}