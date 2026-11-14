package com.eventhub.notification.dto.response;

/**
 * Phản hồi trả về bởi GET /notifications/me/unread-count.
 *
 * @param count Số IN_APP chưa đọc của người đang đăng nhập.
 */
public record UnreadCountResponse(int count) {}
