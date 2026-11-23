package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakTokenClient;
import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUser;
import com.eventhub.auth.client.KeycloakUserClient.RoleRepresentation;
import com.eventhub.auth.common.BusinessRole;
import com.eventhub.auth.common.ErrorCode;
import com.eventhub.auth.dto.request.ChangePasswordRequest;
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
 * rollback cùng DB, nên đăng ký dùng bù trừ (xóa user Keycloak) khi gán role hoặc tạo hồ sơ thất bại.
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    /** Thứ tự ưu tiên khi hiển thị role nếu dữ liệu realm lỗi và user mang nhiều business role. */
    private static final List<String> BUSINESS_ROLES = List.of("ADMIN", "ORGANIZER", "CUSTOMER");

    private final KeycloakTokenClient tokenClient;
    private final KeycloakUserClient userClient;
    private final UserProfileRepository profiles;
    private final EmailRateLimiter emailRateLimiter;

    public AuthServiceImpl(KeycloakTokenClient tokenClient, KeycloakUserClient userClient,
                           UserProfileRepository profiles, EmailRateLimiter emailRateLimiter) {
        this.tokenClient = tokenClient;
        this.userClient = userClient;
        this.profiles = profiles;
        this.emailRateLimiter = emailRateLimiter;
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
            // Gán CUSTOMER trực tiếp: role mặc định của realm không chứa business role nào, nên đổi role sau
            // này chỉ cần gỡ một mapping trực tiếp.
            assignBusinessRole(userId, BusinessRole.CUSTOMER);
            profile = profiles.save(UserProfile.builder()
                    .keycloakSub(userId.toString())
                    .fullName(fullName)
                    .phoneNumber(phone)
                    .build());
        } catch (RuntimeException error) {
            // Hoàn tác để email không bị "kẹt" ở Keycloak mà không có role/hồ sơ EventHub.
            boolean removed = userClient.deleteUserQuietly(userId);
            log.error("Registration failed for new user {}, Keycloak user removed: {}", userId, removed);
            if (error instanceof org.springframework.dao.DataIntegrityViolationException) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number is already registered");
            }
            throw error;
        }
        sendVerificationQuietly(userId);
        return new UserResponse(userId, email, profile.getFullName(), BusinessRole.CUSTOMER.name(), "ACTIVE",
                false, Instant.now());
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
        List<String> businessRoles = BUSINESS_ROLES.stream().filter(roles::contains).toList();
        if (businessRoles.size() > 1) {
            // Các service khác sẽ từ chối token này (401); ghi log để ADMIN sửa role mapping.
            log.warn("User {} has multiple business roles {}", userId, businessRoles);
        }
        String role = businessRoles.isEmpty() ? null : businessRoles.get(0);
        String fullName = profiles.findByKeycloakSub(userId.toString())
                .map(UserProfile::getFullName)
                .orElseGet(() -> joinName(user.lastName(), user.firstName()));
        Instant createdAt = user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp());
        return new UserResponse(userId, user.email(), fullName, role, user.enabled() ? "ACTIVE" : "LOCKED",
                user.emailVerified(), createdAt);
    }

    @Override
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        KeycloakUser user = userClient.getUser(userId);
        if (user.email() == null || !tokenClient.verifyPassword(user.email(), request.currentPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT, "Current password is incorrect");
        }
        userClient.resetPassword(userId, request.newPassword());
    }

    @Override
    public void resendVerificationEmail(UUID userId) {
        KeycloakUser user = userClient.getUser(userId);
        if (user.emailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_VERIFIED, "Email is already verified");
        }
        // Link xác minh không chứa mật khẩu nên không ai khác mở thay được. Không gắn requiredActions trước:
        // send-verify-email đã mang action trong link, gắn trước sẽ chặn đăng nhập của user.
        if (!emailRateLimiter.tryAcquire(user.email().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Verification email was sent recently, please check your inbox");
        }
        userClient.sendVerifyEmail(userId);
    }

    @Override
    public void lockUser(UUID adminId, UUID userId) {
        rejectSelf(adminId, userId);
        KeycloakUser user = userClient.getUser(userId);
        if (!user.enabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_LOCKED, "Account is already locked");
        }
        userClient.setEnabled(userId, false);
        // Thu hồi refresh token để người bị khóa không lấy được access token mới; token đang có hết hạn sau 5 phút.
        userClient.logoutAllSessions(userId);
    }

    @Override
    public void unlockUser(UUID adminId, UUID userId) {
        rejectSelf(adminId, userId);
        KeycloakUser user = userClient.getUser(userId);
        if (user.enabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_ACTIVE, "Account is already active");
        }
        userClient.setEnabled(userId, true);
    }

    @Override
    public void assignRole(UUID adminId, UUID userId, BusinessRole role) {
        rejectSelf(adminId, userId);
        userClient.getUser(userId);
        assignBusinessRole(userId, role);
        List<String> effective = userClient.effectiveRealmRoles(userId);
        List<String> businessRoles = effective.stream().filter(BusinessRole::isBusinessRole).toList();
        if (businessRoles.size() != 1 || !businessRoles.contains(role.name())) {
            // Role đến từ composite (vd default-roles-eventhub của realm cũ còn chứa CUSTOMER), không gỡ được ở user.
            log.error("User {} still has business roles {} after assigning {}", userId, businessRoles, role);
            throw new BusinessException(ErrorCode.ROLE_MAPPING_CONFLICT,
                    "User still inherits another business role from a composite role");
        }
    }

    /**
     * Đặt business role trực tiếp của user thành đúng {@code role}: gỡ các business role khác, thêm role đích nếu
     * chưa có. Thêm trước gỡ sau để user không có lúc nào mất hết role.
     */
    private void assignBusinessRole(UUID userId, BusinessRole role) {
        List<RoleRepresentation> direct = userClient.directRealmRoles(userId);
        boolean hasTarget = direct.stream().anyMatch(r -> role.name().equals(r.name()));
        if (!hasTarget) {
            RoleRepresentation target = userClient.availableRealmRoles(userId).stream()
                    .filter(r -> role.name().equals(r.name()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.error("Realm role {} not found or not assignable by the auth-service account", role);
                        return new BusinessException(ErrorCode.AUTH_PROVIDER_UNAVAILABLE,
                                "Identity provider role configuration is incomplete");
                    });
            userClient.addRealmRoles(userId, List.of(target));
        }
        List<RoleRepresentation> others = direct.stream()
                .filter(r -> BusinessRole.isBusinessRole(r.name()) && !role.name().equals(r.name()))
                .toList();
        userClient.removeRealmRoles(userId, others);
    }

    /** Mail xác minh không chặn đăng ký: SMTP lỗi thì người dùng gửi lại qua /auth/verify-email/resend. */
    private void sendVerificationQuietly(UUID userId) {
        try {
            userClient.sendVerifyEmail(userId);
        } catch (RuntimeException error) {
            log.warn("Could not send verification email to user {}: {}", userId, error.getMessage());
        }
    }

    private static void rejectSelf(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT, "Admins cannot modify their own account");
        }
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
