package com.eventhub.order.dto.response;

import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * C21: payment VNPay đã thành công nhưng không phải payment làm đơn PAID (đơn hết hạn/đã hủy, hoặc khách trả hai
 * lần). ADMIN dùng providerTransactionId/providerPayDate để hoàn tiền thủ công trên cổng VNPay.
 */
public record ReconciliationItem(UUID paymentId, UUID orderId, OrderStatus orderStatus, UUID orderPaymentId,
                                 String customerEmail, BigDecimal amount, String providerTxnRef,
                                 String providerTransactionId, String providerPayDate, Instant updatedAt,
                                 Instant reconciledAt, String reconciliationNote) {

    public static ReconciliationItem from(Payment payment) {
        Order order = payment.getOrder();
        return new ReconciliationItem(payment.getId(), order.getId(), order.getStatus(), order.getPaymentId(),
                order.getCustomerEmail(), payment.getAmount(), payment.getProviderTxnRef(),
                payment.getProviderTransactionId(), payment.getProviderPayDate(), payment.getUpdatedAt(),
                payment.getReconciledAt(), payment.getReconciliationNote());
    }
}
