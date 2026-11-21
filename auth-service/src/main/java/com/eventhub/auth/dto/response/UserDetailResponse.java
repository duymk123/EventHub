package com.eventhub.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết một tài khoản cho trang quản trị.
 *
 * @param requiredActions các bước Keycloak đang bắt user hoàn tất (vd VERIFY_EMAIL, UPDATE_PASSWORD); rỗng nghĩa là
 *                        không còn bước nào chờ.
 */
public record UserDetailResponse(UUID id, String username, String email, String fullName, String role,
                                 String status, boolean emailVerified, List<String> requiredActions,
                                 Instant createdAt) {
}
