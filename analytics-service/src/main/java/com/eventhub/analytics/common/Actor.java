package com.eventhub.analytics.common;

import java.util.UUID;

/**
 * Người dùng đã xác thực, được cung cấp bởi bộ điều hợp Keycloak.
 * Không được tạo từ body hoặc header của request.
 *
 * @param userId UUID tài khoản lấy từ subject của JWT đã xác minh.
 * @param role Một business role đã ánh xạ cho tài khoản hiện tại.
 */
public record Actor(UUID userId, BusinessRole role) {}
