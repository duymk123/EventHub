package com.eventhub.order.controller;

import com.eventhub.order.common.Actor;
import com.eventhub.order.common.RefundStatus;
import com.eventhub.order.dto.request.RefundCreateRequest;
import com.eventhub.order.dto.request.ReviewNoteRequest;
import com.eventhub.order.dto.response.RefundResponse;
import com.eventhub.order.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Hoàn tiền C04/C05 rút gọn: CUSTOMER yêu cầu cho đơn của mình; ADMIN xem, duyệt, từ chối, thử lại.
 */
@RestController
@RequestMapping("/api/v1")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/orders/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse request(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                  @Valid @RequestBody RefundCreateRequest request) {
        return refundService.request(actor, id, request.reason());
    }

    @GetMapping("/orders/{id}/refunds")
    public List<RefundResponse> listForOrder(@AuthenticationPrincipal Actor actor, @PathVariable UUID id) {
        return refundService.listForOrder(actor, id);
    }

    @GetMapping("/admin/refunds")
    public List<RefundResponse> listForAdmin(@RequestParam(required = false) RefundStatus status) {
        return refundService.listForAdmin(status);
    }

    @PostMapping("/admin/refunds/{id}/approve")
    public RefundResponse approve(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                  HttpServletRequest request) {
        return refundService.approve(actor, id, ClientIp.of(request));
    }

    @PostMapping("/admin/refunds/{id}/reject")
    public RefundResponse reject(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                 @Valid @RequestBody ReviewNoteRequest request) {
        return refundService.reject(actor, id, request.reason());
    }

    @PostMapping("/admin/refunds/{id}/retry")
    public RefundResponse retry(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                HttpServletRequest request) {
        return refundService.retry(actor, id, ClientIp.of(request));
    }
}
