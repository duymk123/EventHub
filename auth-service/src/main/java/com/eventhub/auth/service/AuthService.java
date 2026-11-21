package com.eventhub.auth.service;

import com.eventhub.auth.common.BusinessRole;
import com.eventhub.auth.dto.request.ChangePasswordRequest;
import com.eventhub.auth.dto.request.LoginRequest;
import com.eventhub.auth.dto.request.RefreshRequest;
import com.eventhub.auth.dto.request.RegisterRequest;
import com.eventhub.auth.dto.response.TokenResponse;
import com.eventhub.auth.dto.response.UserResponse;

import java.util.UUID;

/**
 * Facade xác thực EventHub → Keycloak (plan Auth §2). Keycloak giữ mật khẩu, phiên và role; Auth Service
 * không tự ký token và không lưu mật khẩu.
 */
public interface AuthService {

    /** Tạo tài khoản Keycloak, gán CUSTOMER, tạo hồ sơ EventHub và gửi mail xác minh. */
    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(RefreshRequest request);

    /** Tài khoản của người đang đăng nhập; userId lấy từ JWT đã xác minh. */
    UserResponse getMe(UUID userId);

    /** AUTH-UC06: kiểm tra mật khẩu hiện tại qua Keycloak rồi đặt mật khẩu mới. */
    void changePassword(UUID userId, ChangePasswordRequest request);

    /** Gửi lại mail xác minh cho người đang đăng nhập; email đã xác minh → EMAIL_ALREADY_VERIFIED. */
    void resendVerificationEmail(UUID userId);

    /** AUTH-UC08: ADMIN khóa tài khoản (disable + thu hồi phiên). */
    void lockUser(UUID adminId, UUID userId);

    /** AUTH-UC09: ADMIN mở khóa tài khoản. */
    void unlockUser(UUID adminId, UUID userId);

    /** AUTH-UC07: ADMIN chuyển business role; user luôn còn đúng một role. */
    void assignRole(UUID adminId, UUID userId, BusinessRole role);
}
