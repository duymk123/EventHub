package com.eventhub.order.controller;

import com.eventhub.order.common.Actor;
import com.eventhub.order.dto.response.OrderResponse;
import com.eventhub.order.dto.response.PaymentResponse;
import com.eventhub.order.dto.response.VnpayIpnAck;
import com.eventhub.order.service.PaymentService;
import com.eventhub.order.service.VnpayIpnService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Thanh toán VNPay (C06): khởi tạo URL, đối soát khi khách quay về, và IPN public cho VNPay.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService paymentService;
    private final VnpayIpnService ipnService;

    public PaymentController(PaymentService paymentService, VnpayIpnService ipnService) {
        this.paymentService = paymentService;
        this.ipnService = ipnService;
    }

    @PostMapping("/orders/{id}/payment")
    public PaymentResponse initiate(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                    HttpServletRequest request) {
        return paymentService.initiate(actor, id, idempotencyKey, ClientIp.of(request));
    }

    @PostMapping("/orders/{id}/payment/verify")
    public OrderResponse verify(@AuthenticationPrincipal Actor actor, @PathVariable UUID id,
                                HttpServletRequest request) {
        return paymentService.verify(actor, id, ClientIp.of(request));
    }

    /** Không JWT; tin cậy dựa trên chữ ký vnp_SecureHash. */
    @GetMapping("/payments/vnpay/ipn")
    public VnpayIpnAck ipn(@RequestParam Map<String, String> params) {
        return ipnService.handle(params);
    }
}
