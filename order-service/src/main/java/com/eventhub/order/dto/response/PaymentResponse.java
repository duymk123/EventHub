package com.eventhub.order.dto.response;

import com.eventhub.order.common.PaymentStatus;
import com.eventhub.order.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * C06: URL thanh toán VNPay đã ký; frontend chuyển khách sang paymentUrl. expiresAt trùng hạn của đơn.
 */
public record PaymentResponse(UUID paymentId, UUID orderId, PaymentStatus status, String provider,
                              String providerTxnRef, String paymentUrl, BigDecimal amount, Instant expiresAt) {

    public static PaymentResponse from(Payment payment, UUID orderId, Instant expiresAt) {
        return new PaymentResponse(payment.getId(), orderId, payment.getStatus(), payment.getProvider(),
                payment.getProviderTxnRef(), payment.getPaymentUrl(), payment.getAmount(), expiresAt);
    }
}
