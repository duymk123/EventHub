package com.eventhub.auth.service;

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

    /** Tạo tài khoản Keycloak (role CUSTOMER mặc định) và hồ sơ EventHub. */
    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout(RefreshRequest request);

    /** Tài khoản của người đang đăng nhập; userId lấy từ JWT đã xác minh. */
    UserResponse getMe(UUID userId);
}
