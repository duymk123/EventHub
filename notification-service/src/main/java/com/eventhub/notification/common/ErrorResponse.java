package com.eventhub.notification.common;

import java.time.Instant;

/**
 * Phản hồi lỗi thống nhất, được trả về bởi tất cả các REST service của EventHub.
 *
 * @param code Mã lỗi nghiệp vụ hoặc mã lỗi HTTP để frontend xử lý.
 * @param message Nội dung lỗi an toàn để trả frontend.
 * @param timestamp Thời điểm tạo response lỗi UTC.
 * @param path Đường dẫn HTTP của request gây lỗi.
 */
public record ErrorResponse(String code, String message, Instant timestamp, String path) {}
