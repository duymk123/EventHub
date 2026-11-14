package com.eventhub.auth.service;

import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;

public interface UserProfileService {
    
    // Lấy thông tin hồ sơ người dùng dựa trên sub của Keycloak
    UserProfileResponse getMyProfile(String keycloakSub);

    // KhoiTao thong tin hồ sơ theo sub
    UserProfileResponse initializeProfile(String keycloakSub, UserProfileRequest request);

    // Cập nhật thông tin hồ sơ theo sub
    UserProfileResponse updateMyProfile(String keycloakSub, UpdateUserProfileRequest request);


}
