package com.eventhub.order.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import com.eventhub.order.dto.request.ReviewNoteRequest;
import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.response.ReconciliationItem;
import com.eventhub.order.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C21: ADMIN xem các khoản VNPay đã trừ tiền nhưng không thành vé để hoàn tiền thủ công.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/reconciliation")
    public List<ReconciliationItem> reconciliation() {
        return paymentService.reconciliation();
    }

    /** Đánh dấu đã hoàn tay trên cổng VNPay; reason là ghi chú cách xử lý. */
    @PostMapping("/{id}/resolve")
    public ReconciliationItem resolve(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                      @Valid @RequestBody ReviewNoteRequest request) {
        return paymentService.resolve(actor, id, request.reason());
    }
}
