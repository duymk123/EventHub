package com.eventhub.notification.common;

import java.util.UUID;

/**
 * Người dùng đã xác thực, được cung cấp bởi bộ điều hợp Keycloak.
 * Không được tạo từ body hoặc header của request.
 */
public record Actor(UUID userId, BusinessRole role) {}
