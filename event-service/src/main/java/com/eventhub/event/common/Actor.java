package com.eventhub.event.common;

import java.util.UUID;

/**
 * Người dùng đã xác thực, tạo từ JWT đã xác minh (sub + business role). Không nhận từ body hay header.
 */
public record Actor(UUID userId, BusinessRole role) {
}
