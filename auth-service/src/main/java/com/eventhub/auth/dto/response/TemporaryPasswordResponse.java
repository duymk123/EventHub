package com.eventhub.auth.dto.response;

import java.util.UUID;

/**
 * Kết quả ADMIN đặt mật khẩu tạm. Mật khẩu chỉ trả đúng một lần trong response này, không lưu và không ghi log;
 * user sẽ được Keycloak yêu cầu đổi ở lần đăng nhập kế tiếp.
 */
public record TemporaryPasswordResponse(UUID userId, String temporaryPassword) {
}
