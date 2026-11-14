package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakTokenClient;
import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUser;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.request.LoginRequest;
import com.eventhub.auth.dto.request.RefreshRequest;
import com.eventhub.auth.dto.request.RegisterRequest;
import com.eventhub.auth.dto.response.TokenResponse;
import com.eventhub.auth.dto.response.UserResponse;
import com.eventhub.auth.entity.UserProfile;
import com.eventhub.auth.exception.BusinessException;
import com.eventhub.auth.repository.UserProfileRepository;
import com.eventhub.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Điều phối giữa Keycloak và bảng user_profiles. Không bọc chuỗi gọi HTTP trong @Transactional: Keycloak không
 * rollback cùng DB, nên đăng ký dùng bù trừ (xóa user Keycloak) khi tạo hồ sơ thất bại.
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final List<String> BUSINESS_ROLES = List.of("ADMIN", "ORGANIZER", "CUSTOMER");

    private final KeycloakTokenClient tokenClient;
    private final KeycloakUserClient userClient;
    private final UserProfileRepository profiles;

    public AuthServiceImpl(KeycloakTokenClient tokenClient, KeycloakUserClient userClient,
                           UserProfileRepository profiles) {
        this.tokenClient = tokenClient;
        this.userClient = userClient;
        this.profiles = profiles;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim().replaceAll("\\s+", " ");
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank()
                ? null : request.phoneNumber().trim();
        // Kiểm tra trước để không tạo user Keycloak rồi mới phát hiện số điện thoại trùng.
        if (phone != null && profiles.existsByPhoneNumber(phone)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered");
        }
        String[] name = splitName(fullName);
        UUID userId = userClient.createUser(email, request.password(), name[0], name[1]);

        UserProfile profile;
        try {
            profile = profiles.save(UserProfile.builder()
                    .keycloakSub(userId.toString())
                    .fullName(fullName)
                    .phoneNumber(phone)
                    .build());
        } catch (RuntimeException error) {
            // Hoàn tác để email không bị "kẹt" ở Keycloak mà không có hồ sơ EventHub.
            boolean removed = userClient.deleteUserQuietly(userId);
            log.error("Profile creation failed for new user {}, Keycloak user removed: {}", userId, removed);
            if (error instanceof org.springframework.dao.DataIntegrityViolationException) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered");
            }
            throw error;
        }
        return new UserResponse(userId, email, profile.getFullName(), "CUSTOMER", "ACTIVE", Instant.now());
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        return tokenClient.login(request.email().trim().toLowerCase(Locale.ROOT), request.password());
    }

    @Override
    public TokenResponse refresh(RefreshRequest request) {
        return tokenClient.refresh(request.refreshToken());
    }

    @Override
    public void logout(RefreshRequest request) {
        tokenClient.logout(request.refreshToken());
    }

    @Override
    public UserResponse getMe(UUID userId) {
        KeycloakUser user = userClient.getUser(userId);
        List<String> roles = userClient.effectiveRealmRoles(userId);
        String role = BUSINESS_ROLES.stream().filter(roles::contains).findFirst().orElse(null);
        String fullName = profiles.findByKeycloakSub(userId.toString())
                .map(UserProfile::getFullName)
                .orElseGet(() -> joinName(user.lastName(), user.firstName()));
        Instant createdAt = user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp());
        return new UserResponse(userId, user.email(), fullName, role, user.enabled() ? "ACTIVE" : "LOCKED", createdAt);
    }

    /**
     * Tên Việt Nam "Nguyễn Văn A": từ đầu là họ (lastName), phần còn lại là tên (firstName). Keycloak yêu cầu cả
     * hai trường; tên một từ thì dùng cho cả hai.
     *
     * @return [firstName, lastName]
     */
    static String[] splitName(String fullName) {
        int space = fullName.indexOf(' ');
        if (space < 0) {
            return new String[]{fullName, fullName};
        }
        return new String[]{fullName.substring(space + 1), fullName.substring(0, space)};
    }

    private static String joinName(String lastName, String firstName) {
        return ((lastName == null ? "" : lastName) + " " + (firstName == null ? "" : firstName)).trim();
    }
}
