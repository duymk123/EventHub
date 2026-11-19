package com.eventhub.auth.service.impl;

import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;
import com.eventhub.auth.entity.UserProfile;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.mapper.UserProfileMapper;
import com.eventhub.auth.repository.UserProfileRepository;
import com.eventhub.auth.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hồ sơ nghiệp vụ theo Keycloak sub. UNIQUE(keycloak_sub) và UNIQUE(phone_number) ở DB là chốt cuối khi hai
 * request chạy song song; vi phạm được đổi thành 409 thay vì 500.
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String keycloakSub) {
        return userProfileMapper.toResponse(find(keycloakSub));
    }

    @Override
    @Transactional
    public UserProfileResponse initializeProfile(String keycloakSub, UserProfileRequest request) {
        if (userProfileRepository.findByKeycloakSub(keycloakSub).isPresent()) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS, "Profile already exists");
        }
        String phone = blankToNull(request.getPhoneNumber());
        requirePhoneFree(phone, null);
        UserProfile profile = userProfileMapper.toEntity(request);
        profile.setKeycloakSub(keycloakSub);
        profile.setFullName(request.getFullName().trim());
        profile.setPhoneNumber(phone);
        try {
            return userProfileMapper.toResponse(userProfileRepository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(ErrorCode.PROFILE_ALREADY_EXISTS, "Profile or phone number already exists");
        }
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String keycloakSub, UpdateUserProfileRequest request) {
        UserProfile profile = find(keycloakSub);
        if (request.getPhoneNumber() != null) {
            String phone = blankToNull(request.getPhoneNumber());
            requirePhoneFree(phone, profile);
            // Gán tay để chuỗi rỗng xóa được số điện thoại (mapper bỏ qua null).
            request.setPhoneNumber(null);
            profile.setPhoneNumber(phone);
        }
        if (request.getFullName() != null) {
            if (request.getFullName().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fullName: must not be blank");
            }
            request.setFullName(request.getFullName().trim());
        }
        userProfileMapper.updateEntity(request, profile);
        try {
            return userProfileMapper.toResponse(userProfileRepository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered");
        }
    }

    private UserProfile find(String keycloakSub) {
        return userProfileRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Profile not found"));
    }

    private void requirePhoneFree(String phone, UserProfile owner) {
        if (phone == null || (owner != null && phone.equals(owner.getPhoneNumber()))) {
            return;
        }
        if (userProfileRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
