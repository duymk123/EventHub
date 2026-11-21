package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.AssignRoleRequest;
import com.eventhub.auth.dto.response.PageUserResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.dto.response.UserSessionsResponse;
import com.eventhub.auth.security.Actor;
import com.eventhub.auth.security.BusinessRole;
import com.eventhub.auth.service.UserAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin User Controller - Admin-only user management endpoints.
 * All endpoints under /api/v1/admin/users.
 * Requires ADMIN role (enforced by @PreAuthorize and SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdministrationService userAdminService;

    @GetMapping
    public ResponseEntity<PageUserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BusinessRole role,
            @RequestParam(required = false) UserResponse.UserStatus status,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        return userAdminService.listUsers(admin, page, size, search, role, status)
                .map(ResponseEntity::ok)
                .block();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> getUserDetail(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        return userAdminService.getUserDetail(admin, userId)
                .map(ResponseEntity::ok)
                .block();
    }

    @PatchMapping("/{userId}/lock")
    public ResponseEntity<Void> lockUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        userAdminService.lockUser(admin, userId).block();
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/unlock")
    public ResponseEntity<Void> unlockUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        userAdminService.unlockUser(admin, userId).block();
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        userAdminService.assignRole(admin, userId, request).block();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Void> adminResetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminResetPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        userAdminService.adminResetPassword(admin, userId, request).block();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/sessions")
    public ResponseEntity<UserSessionsResponse> getUserSessions(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        return userAdminService.getUserSessions(admin, userId)
                .map(ResponseEntity::ok)
                .block();
    }

    @DeleteMapping("/{userId}/sessions")
    public ResponseEntity<Void> revokeAllUserSessions(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        Actor admin = extractActor(jwt);
        userAdminService.revokeAllUserSessions(admin, userId).block();
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract Actor from JWT.
     * In production, this should use IdentityMapper bean.
     */
    private Actor extractActor(Jwt jwt) {
        String sub = jwt.getSubject();
        var realmAccess = jwt.getClaim("realm_access");
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) ((java.util.Map<String, Object>) realmAccess).get("roles");
        BusinessRole role = roles.stream()
                .filter(r -> r.equals("CUSTOMER") || r.equals("ORGANIZER") || r.equals("ADMIN"))
                .map(BusinessRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No business role in token"));
        return new Actor(UUID.fromString(sub), role);
    }
}