package com.eventhub.analytics.controller;

import com.eventhub.analytics.common.Actor;
import com.eventhub.analytics.common.EventAnalyticsSort;
import com.eventhub.analytics.common.Granularity;
import com.eventhub.analytics.dto.response.AnalyticsOverviewResponse;
import com.eventhub.analytics.dto.response.EventAnalyticsRow;
import com.eventhub.analytics.service.PlatformAnalyticsService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thống kê gộp cho trang Phân tích (C10, plan Analytics §8): ADMIN xem mọi sự kiện, ORGANIZER chỉ sự kiện của
 * mình. Tách khỏi AnalyticsController vì controller đó map /api/v1/analytics/events/{id}/...; hai path ở đây
 * không có {id} nên không trùng mapping.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class PlatformAnalyticsController {

    private final PlatformAnalyticsService service;

    public PlatformAnalyticsController(PlatformAnalyticsService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/analytics/overview?from=&to=&granularity=DAY|MONTH[&eventId=]
     * Tổng, kỳ trước, chuỗi theo ngày/tháng đã điền đủ kỳ và phân bổ theo hạng vé.
     */
    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @AuthenticationPrincipal Actor actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam Granularity granularity,
            @RequestParam(required = false) UUID eventId) {
        return ResponseEntity.ok(service.getOverview(actor, from, to, granularity, eventId));
    }

    /**
     * GET /api/v1/analytics/events?from=&to=&sort=&page=&size=
     * page/size/sort được nhận tường minh thay vì Pageable: sort ở đây là tên số liệu (luôn giảm dần) chứ không
     * phải thuộc tính entity, nếu để Pageable tự đọc sẽ hiểu thành sắp xếp tăng dần theo thuộc tính.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<EventAnalyticsRow>> listEvents(
            @AuthenticationPrincipal Actor actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listEvents(actor, from, to, EventAnalyticsSort.fromParam(sort), page, size));
    }
}
