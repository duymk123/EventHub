package com.eventhub.auth.controller;

import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;
import com.eventhub.auth.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hồ sơ nghiệp vụ của người đang đăng nhập, khóa theo Keycloak sub trong JWT.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /** GET /api/v1/me/profile → 200, chưa có hồ sơ → 404 PROFILE_NOT_FOUND. */
    @GetMapping
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userProfileService.getMyProfile(jwt.getSubject()));
    }

    /**
     * PUT /api/v1/me/profile → 201. Dành cho tài khoản không đi qua /auth/register (tài khoản test nạp từ realm,
     * user tạo trên trang quản trị Keycloak). Đã có hồ sơ → 409 PROFILE_ALREADY_EXISTS.
     */
    @PutMapping
    public ResponseEntity<UserProfileResponse> initializeProfile(@AuthenticationPrincipal Jwt jwt,
                                                                 @Valid @RequestBody UserProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.initializeProfile(jwt.getSubject(), request));
    }

    /** PATCH /api/v1/me/profile → 200, chỉ cập nhật trường được gửi. */
    @PatchMapping
    public ResponseEntity<UserProfileResponse> updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateMyProfile(jwt.getSubject(), request));
    }
}
