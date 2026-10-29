package com.eventhub.notification.controller;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.dto.response.NotificationResponse;
import com.eventhub.notification.dto.response.UnreadCountResponse;
import com.eventhub.notification.service.NotificationService;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Các endpoint thông báo dành cho người dùng.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/notifications/me?unreadOnly=&page=&size=&sort=
     * Mục đích: Danh sách thông báo in-app của tôi, mới nhất trước.
     * unreadOnly=true chỉ lấy thông báo chưa đọc; mặc định false lấy cả đã đọc và chưa đọc.
     * page bắt đầu từ 0 (mặc định 0); size là số thông báo mỗi trang (mặc định 20, giới hạn 1–100).
     * sort mặc định createdAt,desc và id,desc; có thể truyền lặp lại để sắp xếp theo nhiều trường.
     * Trả Page thông báo với content và thông tin phân trang trong page.
     */
    @GetMapping("/me")
    public ResponseEntity<Page<NotificationResponse>> getMine(
            @AuthenticationPrincipal Actor actor,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<NotificationResponse> response = notificationService.getMine(actor, unreadOnly, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/notifications/{id}/read
     * Mục đích: Đánh dấu một thông báo in-app của tôi là đã đọc.
     * Trả 204 khi thành công, kể cả đã đọc; trả 404 nếu không tìm thấy thông báo in-app của tôi với ID này.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") UUID id) {
        notificationService.markRead(actor, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/notifications/me/unread-count
     * Mục đích: Đếm số thông báo in-app chưa đọc của tôi để hiển thị trên biểu tượng thông báo.
     */
    @GetMapping("/me/unread-count")
    public ResponseEntity<UnreadCountResponse> countUnread(
            @AuthenticationPrincipal Actor actor) {
        UnreadCountResponse response = notificationService.countUnread(actor);
        return ResponseEntity.ok(response);
    }
}
