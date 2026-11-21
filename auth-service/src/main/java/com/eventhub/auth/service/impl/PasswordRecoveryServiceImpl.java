package com.eventhub.auth.service.impl;

import com.eventhub.auth.client.KeycloakUserClient;
import com.eventhub.auth.client.KeycloakUserClient.KeycloakUser;
import com.eventhub.auth.dto.request.ForgotPasswordRequest;
import com.eventhub.auth.service.PasswordRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Quên mật khẩu: tìm user theo email rồi nhờ Keycloak gửi link đặt lại. Không có endpoint đặt mật khẩu bằng token
 * ở Auth Service vì token do Keycloak cấp và chỉ Keycloak xác nhận được; thêm endpoint như vậy sẽ phải tin một
 * token mà service không kiểm chứng được.
 */
@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    private final KeycloakUserClient userClient;

    public PasswordRecoveryServiceImpl(KeycloakUserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        KeycloakUser user = userClient.findByEmailExact(email);
        if (user == null) {
            // Không phân biệt với trường hợp gửi mail thành công: tránh dò tài khoản qua endpoint công khai.
            log.info("Password reset requested for an address without an account");
            return;
        }
        UUID userId = UUID.fromString(user.id());
        if (!user.enabled()) {
            // Tài khoản bị khóa: không gửi mail đặt lại mật khẩu, cũng không báo lỗi để giữ phản hồi đồng nhất.
            log.info("Password reset requested for a disabled account {}", userId);
            return;
        }
        userClient.sendUpdatePasswordEmail(userId);
    }
}
