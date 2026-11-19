package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.AssignRoleRequest;
import com.eventhub.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AUTH-UC07..UC09, chỉ ADMIN (SecurityConfig chặn /api/v1/admin/**). userId là Keycloak sub.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/{userId}/lock")
    public ResponseEntity<Void> lock(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId) {
        authService.lockUser(UUID.fromString(admin.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/unlock")
    public ResponseEntity<Void> unlock(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId) {
        authService.unlockUser(UUID.fromString(admin.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> assignRole(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId,
                                           @Valid @RequestBody AssignRoleRequest request) {
        authService.assignRole(UUID.fromString(admin.getSubject()), userId, request.role());
        return ResponseEntity.noContent().build();
    }
}
