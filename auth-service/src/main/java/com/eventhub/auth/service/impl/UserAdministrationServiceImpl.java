package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.dto.KCUserRepresentation;
import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.AssignRoleRequest;
import com.eventhub.auth.dto.response.PageUserResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.dto.response.UserSessionsResponse;
import com.eventhub.auth.security.Actor;
import com.eventhub.auth.security.BusinessRole;
import com.eventhub.auth.service.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Administration Service implementation.
 * Admin-only operations for user management via Keycloak Admin API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdministrationServiceImpl implements UserAdministrationService {

    private final KeycloakUserClient keycloakUserClient;

    @Override
    public Mono<PageUserResponse> listUsers(Actor admin, int page, int size, String search, BusinessRole role, UserResponse.UserStatus status) {
        int first = page * size;
        log.debug("Admin {} listing users: page={}, size={}, search={}, role={}, status={}",
                admin.userId(), page, size, search, role, status);

        return keycloakUserClient.getUsers(first, size, search)
                .map(users -> {
                    List<UserResponse> filtered = users.stream()
                            .map(this::mapToUserResponse)
                            .filter(u -> role == null || u.getRole() == role)
                            .filter(u -> status == null || u.getStatus() == status)
                            .collect(Collectors.toList());

                    return PageUserResponse.builder()
                            .content(filtered)
                            .page(PageUserResponse.PageInfo.builder()
                                    .size(size)
                                    .number(page)
                                    .totalElements(filtered.size())
                                    .totalPages(size > 0 ? (int) Math.ceil((double) filtered.size() / size) : 0)
                                    .build())
                            .build();
                });
    }

    @Override
    public Mono<UserDetailResponse> getUserDetail(Actor admin, UUID userId) {
        log.debug("Admin {} getting user detail: {}", admin.userId(), userId);

        return keycloakUserClient.getUser(userId.toString())
                .map(this::mapToUserDetailResponse);
    }

    @Override
    public Mono<Void> lockUser(Actor admin, UUID userId) {
        log.info("Admin {} locking user: {}", admin.userId(), userId);

        return keycloakUserClient.setEnabled(userId.toString(), false)
                .then(keycloakUserClient.revokeAllSessions(userId.toString()))
                .doOnSuccess(v -> log.info("User locked and sessions revoked: {}", userId));
    }

    @Override
    public Mono<Void> unlockUser(Actor admin, UUID userId) {
        log.info("Admin {} unlocking user: {}", admin.userId(), userId);

        return keycloakUserClient.setEnabled(userId.toString(), true)
                .doOnSuccess(v -> log.info("User unlocked: {}", userId));
    }

    @Override
    public Mono<Void> assignRole(Actor admin, UUID userId, AssignRoleRequest request) {
        log.info("Admin {} assigning role {} to user: {}", admin.userId(), request.getRole(), userId);

        return keycloakUserClient.assignRole(userId.toString(), request.getRole().name())
                .doOnSuccess(v -> log.info("Role assigned successfully: {} -> {}", userId, request.getRole()));
    }

    @Override
    public Mono<Void> adminResetPassword(Actor admin, UUID userId, AdminResetPasswordRequest request) {
        log.info("Admin {} resetting password for user: {} (action: {})", admin.userId(), userId, request.getAction());

        String action = request.getAction() == AdminResetPasswordRequest.Action.SEND_EMAIL
                ? "SEND_EMAIL"
                : "SET_TEMPORARY_PASSWORD";

        return keycloakUserClient.resetPassword(userId.toString(), action, request.getTemporaryPassword())
                .doOnSuccess(v -> log.info("Password reset action completed for user: {}", userId));
    }

    @Override
    public Mono<UserSessionsResponse> getUserSessions(Actor admin, UUID userId) {
        log.debug("Admin {} getting sessions for user: {}", admin.userId(), userId);

        return keycloakUserClient.getUserSessions(userId.toString())
                .map(kcResponse -> UserSessionsResponse.builder()
                        .sessions(kcResponse.getSessions().stream()
                                .map(s -> UserSessionsResponse.SessionInfo.builder()
                                        .id(s.getId())
                                        .startedAt(s.getStart() != null ? Instant.ofEpochMilli(s.getStart()) : null)
                                        .lastAccessedAt(s.getLastAccess() != null ? Instant.ofEpochMilli(s.getLastAccess()) : null)
                                        .ipAddress(s.getIpAddress())
                                        .userAgent(s.getUserAgent())
                                        .device(s.getDevice())
                                        .build())
                                .toList())
                        .build());
    }

    @Override
    public Mono<Void> revokeAllUserSessions(Actor admin, UUID userId) {
        log.info("Admin {} revoking all sessions for user: {}", admin.userId(), userId);

        return keycloakUserClient.revokeAllSessions(userId.toString())
                .doOnSuccess(v -> log.info("All sessions revoked for user: {}", userId));
    }

    private UserResponse mapToUserResponse(KCUserRepresentation user) {
        return UserResponse.builder()
                .id(UUID.fromString(user.getId()))
                .email(user.getEmail())
                .fullName(buildFullName(user.getFirstName(), user.getLastName()))
                .role(extractBusinessRole(user.getRealmRoles()))
                .status(user.getEnabled() != null && user.getEnabled() ? UserResponse.UserStatus.ACTIVE : UserResponse.UserStatus.LOCKED)
                .createdAt(user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null)
                .build();
    }

    private UserDetailResponse mapToUserDetailResponse(KCUserRepresentation user) {
        return UserDetailResponse.builder()
                .id(UUID.fromString(user.getId()))
                .email(user.getEmail())
                .fullName(buildFullName(user.getFirstName(), user.getLastName()))
                .role(extractBusinessRole(user.getRealmRoles()))
                .status(user.getEnabled() != null && user.getEnabled() ? UserDetailResponse.UserStatus.ACTIVE : UserDetailResponse.UserStatus.LOCKED)
                .createdAt(user.getCreatedTimestamp() != null ? Instant.ofEpochMilli(user.getCreatedTimestamp()) : null)
                .emailVerified(user.getEmailVerified() != null && user.getEmailVerified())
                .username(user.getUsername())
                .build();
    }

    private String buildFullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) return "";
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    private BusinessRole extractBusinessRole(List<String> realmRoles) {
        if (realmRoles == null || realmRoles.isEmpty()) {
            return BusinessRole.CUSTOMER;
        }
        return realmRoles.stream()
                .filter(r -> r.equals("CUSTOMER") || r.equals("ORGANIZER") || r.equals("ADMIN"))
                .map(BusinessRole::valueOf)
                .findFirst()
                .orElse(BusinessRole.CUSTOMER);
    }
}