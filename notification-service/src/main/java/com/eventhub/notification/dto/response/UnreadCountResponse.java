package com.eventhub.notification.dto.response;

/**
 * Phản hồi trả về bởi GET /notifications/me/unread-count.
 */
public record UnreadCountResponse(int count) {}
