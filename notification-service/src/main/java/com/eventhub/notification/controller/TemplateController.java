package com.eventhub.notification.controller;

import com.eventhub.notification.common.Actor;
import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.dto.request.TemplateRequest;
import com.eventhub.notification.dto.response.TemplateResponse;
import com.eventhub.notification.service.TemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Các endpoint quản lý template dành cho admin.
 * Chỉ vai trò ADMIN được phép truy cập.
 */
@RestController
@RequestMapping("/api/v1/admin/notification-templates")
@Validated
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * GET /api/v1/admin/notification-templates
     * Mục đích: Danh sách mẫu thông báo, chỉ dành cho ADMIN.
     * Kết quả sắp xếp theo mã mẫu rồi đến kênh gửi.
     */
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list(
            @AuthenticationPrincipal Actor actor) {
        List<TemplateResponse> response = templateService.list(actor);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/notification-templates/{code}?channel=
     * Mục đích: Xem chi tiết một mẫu thông báo theo mã và kênh gửi, chỉ dành cho ADMIN.
     * channel bắt buộc, nhận EMAIL hoặc IN_APP; trả 404 nếu mẫu không tồn tại.
     */
    @GetMapping("/{code}")
    public ResponseEntity<TemplateResponse> get(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("code") @Pattern(regexp = "^[A-Z_]+$") String code,
            @RequestParam NotificationChannel channel) {
        TemplateResponse response = templateService.get(actor, code, channel);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/admin/notification-templates/{code}
     * Mục đích: Thêm mới hoặc cập nhật mẫu thông báo theo mã và kênh gửi, chỉ dành cho ADMIN.
     * Body chứa channel, subject và bodyTemplate; cập nhật nếu đã có cùng code và channel, tạo mới nếu chưa có.
     */
    @PutMapping("/{code}")
    public ResponseEntity<TemplateResponse> upsert(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("code") @Pattern(regexp = "^[A-Z_]+$") String code,
            @Valid @RequestBody TemplateRequest request) {
        TemplateResponse response = templateService.upsert(actor, code, request);
        return ResponseEntity.ok(response);
    }
}
