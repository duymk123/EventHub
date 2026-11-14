package com.eventhub.auth.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;
import com.eventhub.auth.entity.UserProfile;
import com.eventhub.auth.mapper.UserProfileMapper;
import com.eventhub.auth.repository.UserProfileRepository;
import com.eventhub.auth.service.UserProfileService;

import lombok.RequiredArgsConstructor;

// Note:
// Khi hoàn thiện cần thay thế RuntimeException bằng các exception cụ thể hơn, ví dụ như UserProfileNotFoundException, UserProfileAlreadyExistsException, v.v.
// XỬ lý Race Condition: Khi nhiều request cùng lúc tạo hồ sơ người dùng mới, có thể xảy ra tình trạng trùng lặp sub. Để xử lý, có thể sử dụng cơ chế khóa (lock) hoặc kiểm tra tồn tại trước khi lưu. Ngoài ra, cần đảm bảo rằng các thao tác liên quan đến cơ sở dữ liệu được thực hiện trong một transaction để tránh tình trạng dữ liệu không nhất quán.
@Service 
@RequiredArgsConstructor 
public class UserProfileServiceImpl implements UserProfileService{
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    // Lấy thông tin hồ sơ người dùng dựa trên sub của Keycloak
    @Override
    public UserProfileResponse getMyProfile(String keycloakSub) {
        return userProfileMapper.toResponse(userProfileRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new RuntimeException("User profile not found for sub: " + keycloakSub)));
    }

    // Khởi tạo hồ sơ người dùng mới dựa trên sub của Keycloak và thông tin từ UserProfileRequest
    @Override
    @Transactional 
    public UserProfileResponse initializeProfile(String keycloakSub, UserProfileRequest request) {
        userProfileRepository.findByKeycloakSub(keycloakSub).ifPresent(existingProfile -> {
            throw new RuntimeException("User profile already exists for sub: " + keycloakSub);
        });

        // UserProfile newProfile = UserProfile.builder()
        //         .keycloakSub(keycloakSub)
        //         .fullName(request.getFullName())
        //         .phoneNumber(request.getPhoneNumber())
        //         .avatarUrl(request.getAvatarUrl())
        //         .build();

        UserProfile newProfile = userProfileMapper.toEntity(request);
           
        newProfile.setKeycloakSub(keycloakSub);

        userProfileRepository.save(newProfile);

        return userProfileMapper.toResponse(newProfile);

    }

    // Cập nhật hồ sơ người dùng dựa trên sub của Keycloak và thông tin từ UpdateUserProfileRequest
    @Override
    @Transactional 
    public UserProfileResponse updateMyProfile(String keycloakSub, UpdateUserProfileRequest request) {
        UserProfile existingProfile = userProfileRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new RuntimeException("User profile not found for sub: " + keycloakSub));

        userProfileMapper.updateEntity(request, existingProfile);

        UserProfile updatedProfile = userProfileRepository.save(existingProfile);

        return toResponse(updatedProfile);
    }

    // Nếu k dùng mapstruct
    // Chuyển đổi từ UserProfile entity sang UserProfileResponse DTO
        private UserProfileResponse toResponse(UserProfile newProfile) {
            return UserProfileResponse.builder()
                    .id(newProfile.getId())
                    .keycloakSub(newProfile.getKeycloakSub())
                    .fullName(newProfile.getFullName())
                    .phoneNumber(newProfile.getPhoneNumber())
                    .avatarUrl(newProfile.getAvatarUrl())
                    .build();
        }
    }
