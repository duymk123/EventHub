package com.eventhub.auth.controller;

import org.springframework.web.bind.annotation.RestController;

import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;
import com.eventhub.auth.service.impl.UserProfileServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequiredArgsConstructor 
@RequestMapping("/api/v1/me/profile")
public class UserProfileController {

    private final UserProfileServiceImpl userProfileServiceImpl;

    // GET /api/v1/me/profile
    @GetMapping()
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String keycloakSub = jwt.getSubject();

        UserProfileResponse userProfile = userProfileServiceImpl.getMyProfile(keycloakSub);
        return ResponseEntity.ok(userProfile);
    }

    // Khởi tạo hồ sơ nghiệp vụ: tạo bản ghi tương ứng với tài khoản Ketcloak
    // PUT /api/v1/me/profile
    @PutMapping()
    public ResponseEntity<String> initializeProfile(
            @AuthenticationPrincipal Jwt jwt, 
            @Valid  @RequestBody UserProfileRequest request) {
        
        String keycloakSub = jwt.getSubject();
        userProfileServiceImpl.initializeProfile(keycloakSub, request);

        return ResponseEntity.ok("User profile initialized successfully.");
    }

    // PATCH /api/v1/me/profile
    @PatchMapping ()
    public ResponseEntity<String> updateMyProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserProfileRequest request) {
        String keycloakSub = jwt.getSubject();
        userProfileServiceImpl.updateMyProfile(keycloakSub, request);
        return ResponseEntity.ok("User profile updated successfully.");
    }

}

