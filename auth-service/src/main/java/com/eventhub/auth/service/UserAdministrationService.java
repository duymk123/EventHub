package com.eventhub.auth.service;

import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.AssignRoleRequest;
import com.eventhub.auth.dto.response.PageUserResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.dto.response.UserSessionsResponse;
import com.eventhub.auth.security.Actor;
import com.eventhub.auth.security.BusinessRole;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * User Administration Service interface for admin operations.
 * Requires ADMIN role. All methods are reactive.
 */
public interface UserAdministrationService {
    /**
     * List users with pagination, search, and filters.
     */
    Mono<PageUserResponse> listUsers(Actor admin, int page, int size, String search, BusinessRole role, UserResponse.UserStatus status);

    /**
     * Get detailed user info by ID.
     */
    Mono<UserDetailResponse> getUserDetail(Actor admin, UUID userId);

    /**
     * Lock user account (disable in Keycloak) and revoke all sessions.
     */
    Mono<Void> lockUser(Actor admin, UUID userId);

    /**
     * Unlock user account (enable in Keycloak).
     */
    Mono<Void> unlockUser(Actor admin, UUID userId);

    /**
     * Assign business role to user (replaces existing business role).
     */
    Mono<Void> assignRole(Actor admin, UUID userId, AssignRoleRequest request);

    /**
     * Admin reset password: send email or set temporary password.
     */
    Mono<Void> adminResetPassword(Actor admin, UUID userId, AdminResetPasswordRequest request);

    /**
     * Get user's active sessions.
     */
    Mono<UserSessionsResponse> getUserSessions(Actor admin, UUID userId);

    /**
     * Revoke all sessions of a user.
     */
    Mono<Void> revokeAllUserSessions(Actor admin, UUID userId);
}