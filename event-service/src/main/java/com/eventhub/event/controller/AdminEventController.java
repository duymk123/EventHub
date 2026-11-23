package com.eventhub.event.controller;

import com.eventhub.event.common.Actor;
import com.eventhub.event.common.EventStatus;
import com.eventhub.event.dto.request.ReviewRequest;
import com.eventhub.event.dto.response.EventResponse;
import com.eventhub.event.dto.response.EventStatusResponse;
import com.eventhub.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** EVT-11 (danh sách) và EVT-04 (duyệt), chỉ ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * EVT-11. Mặc định trang 20 theo ngày cập nhật mới nhất; không lọc status thì trả mọi trạng thái, client
     * thường dùng ?status=SUBMITTED cho hàng đợi duyệt.
     */
    @GetMapping
    public Page<EventResponse> list(@RequestParam(required = false) EventStatus status,
                                    @PageableDefault(size = 20) Pageable pageable) {
        return eventService.getAll(status, pageable);
    }

    @PatchMapping("/{id}/review")
    public EventStatusResponse review(@AuthenticationPrincipal Actor admin, @PathVariable UUID id,
                                      @Valid @RequestBody ReviewRequest request) {
        return eventService.review(admin, id, request);
    }
}
