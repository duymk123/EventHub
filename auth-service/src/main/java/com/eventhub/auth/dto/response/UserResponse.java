package com.eventhub.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin tài khoản: id/email/trạng thái/ngày tạo lấy từ Keycloak, fullName từ hồ sơ EventHub.
 *
 * @param id Keycloak user id (sub trong JWT), là userId dùng xuyên các service.
 * @param role CUSTOMER, ORGANIZER hoặc ADMIN.
 * @param status ACTIVE hoặc LOCKED (user bị disable trong Keycloak).
 * @param emailVerified false cho tới khi người dùng bấm link trong mail xác minh; Order chỉ cho đặt vé khi true.
 */
public record UserResponse(UUID id, String email, String fullName, String role, String status,
                           boolean emailVerified, Instant createdAt) {}
