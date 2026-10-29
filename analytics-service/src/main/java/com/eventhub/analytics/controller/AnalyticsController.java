package com.eventhub.analytics.controller;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.dto.response.AttendanceResponse;
import com.eventhub.analytics.dto.response.DashboardSnapshotResponse;
import com.eventhub.analytics.dto.response.SalesReportResponse;
import com.eventhub.analytics.service.AnalyticsQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Các endpoint REST cho phân tích số liệu.
 * Controller nhận Principal từ request và chuyển cho lớp service xử lý.
 */
@RestController
@RequestMapping("/api/v1/analytics/events")
@Validated
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * GET /api/v1/analytics/events/{id}/sales?from=&to=
     * Mục đích: Xem báo cáo doanh số sự kiện theo khoảng thời gian, chi tiết theo ngày và hạng vé.
     * Dành cho ADMIN hoặc ORGANIZER sở hữu sự kiện.
     * from/to có dạng yyyy-MM-dd; bỏ cận nào thì không giới hạn cận đó; from sau to trả 400.
     */
    @GetMapping("/{id}/sales")
    public ResponseEntity<SalesReportResponse> getSales(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SalesReportResponse response = queryService.getSales(actor, id, from, to);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/events/{id}/attendance
     * Mục đích: Xem số người đang có mặt và tổng số vé đã check-in của sự kiện.
     * Dành cho ADMIN hoặc ORGANIZER sở hữu sự kiện; chưa có dữ liệu thống kê trả số 0 và updatedAt=null.
     */
    @GetMapping("/{id}/attendance")
    public ResponseEntity<AttendanceResponse> getAttendance(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") UUID id) {
        AttendanceResponse response = queryService.getAttendance(actor, id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/events/{id}/dashboard
     * Mục đích: Lấy số liệu tổng quan ban đầu cho dashboard trước khi nhận cập nhật qua WebSocket.
     * Dành cho ADMIN hoặc ORGANIZER sở hữu sự kiện; gồm doanh thu, vé bán và số liệu điểm danh.
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<DashboardSnapshotResponse> getDashboard(
            @AuthenticationPrincipal Actor actor,
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") UUID id) {
        DashboardSnapshotResponse response = queryService.getDashboard(actor, id);
        return ResponseEntity.ok(response);
    }
}
