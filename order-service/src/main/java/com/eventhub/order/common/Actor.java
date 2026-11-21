package com.eventhub.order.common;

import java.util.UUID;

/**
 * Người dùng đã xác thực, tạo từ JWT đã xác minh. email chỉ có khi token báo email_verified = true (C02:
 * Order snapshot email buyer đã xác minh). Không nhận từ body hay header.
 */
public record Actor(UUID userId, BusinessRole role, String email) {
}
