package com.eventhub.auth.controller;

import com.eventhub.auth.common.BusinessRole;
import com.eventhub.auth.dto.request.AdminResetPasswordRequest;
import com.eventhub.auth.dto.request.AssignRoleRequest;
import com.eventhub.auth.dto.request.UserListQuery;
import com.eventhub.auth.dto.response.PageResponse;
import com.eventhub.auth.dto.response.TemporaryPasswordResponse;
import com.eventhub.auth.dto.response.UserDetailResponse;
import com.eventhub.auth.service.AdminUserService;
import com.eventhub.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AUTH-UC07..UC09, chỉ ADMIN (SecurityConfig chặn /api/v1/admin/**). userId là Keycloak sub.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AdminUserController(AuthService authService, AdminUserService adminUserService) {
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    /**
     * GET /api/v1/admin/users?page=0&size=20&search=&role=ORGANIZER&status=ACTIVE
     * → một trang tài khoản; tham số không hợp lệ được chuẩn hóa thay vì trả lỗi.
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserDetailResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BusinessRole role,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminUserService.listUsers(UserListQuery.of(page, size, search, role, status)));
    }

    /** GET /api/v1/admin/users/{userId} → chi tiết tài khoản; không tồn tại → 404 USER_NOT_FOUND. */
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> detail(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }

    /**
     * POST /api/v1/admin/users/{userId}/reset-password
     * {"action":"SEND_EMAIL"} → 200, Keycloak gửi mail đặt mật khẩu.
     * {"action":"SET_TEMPORARY_PASSWORD"} → 200 kèm temporaryPassword, mật khẩu chỉ trả một lần.
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<TemporaryPasswordResponse> resetPassword(@AuthenticationPrincipal Jwt admin,
                                                                   @PathVariable UUID userId,
                                                                   @Valid @RequestBody AdminResetPasswordRequest request) {
        return ResponseEntity.ok(adminUserService.resetPassword(UUID.fromString(admin.getSubject()), userId, request));
    }

    // PATCH /api/v1/admin/users/{userId}/lock → 204, không tồn tại → 404 USER_NOT_FOUND.
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<Void> lock(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId) {
        authService.lockUser(UUID.fromString(admin.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }

    // PATCH /api/v1/admin/users/{userId}/unlock → 204, không tồn tại → 404 USER_NOT_FOUND.
    @PatchMapping("/{userId}/unlock")
    public ResponseEntity<Void> unlock(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId) {
        authService.unlockUser(UUID.fromString(admin.getSubject()), userId);
        return ResponseEntity.noContent().build();
    }

    //  PATCH /api/v1/admin/users/{userId}/role → 204, không tồn tại → 404 USER_NOT_FOUND.
    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> assignRole(@AuthenticationPrincipal Jwt admin, @PathVariable UUID userId,
                                           @Valid @RequestBody AssignRoleRequest request) {
        authService.assignRole(UUID.fromString(admin.getSubject()), userId, request.role());
        return ResponseEntity.noContent().build();
    }
}
