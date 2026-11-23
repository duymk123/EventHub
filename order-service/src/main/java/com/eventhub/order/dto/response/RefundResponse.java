package com.eventhub.order.dto.response;

import com.eventhub.order.common.RefundStatus;
import com.eventhub.order.entity.RefundRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * C04/C05 rút gọn: yêu cầu hoàn cả đơn 70%. failureReason là mã lỗi (TICKET_ALREADY_CHECKED_IN, VNPAY_xx,
 * PAYMENT_PROVIDER_UNAVAILABLE…), không chứa dữ liệu nhạy cảm.
 */
public record RefundResponse(UUID id, UUID orderId, RefundStatus status, BigDecimal amount, String reason,
                             UUID requestedBy, UUID approvedBy, Instant requestedAt, Instant reviewedAt,
                             Instant refundedAt, String reviewReason, String failureReason) {

    public static RefundResponse from(RefundRequest refund) {
        return new RefundResponse(refund.getId(), refund.getOrder().getId(), refund.getStatus(), refund.getAmount(),
                refund.getReason(), refund.getRequestedBy(), refund.getApprovedBy(), refund.getRequestedAt(),
                refund.getReviewedAt(), refund.getRefundedAt(), refund.getReviewReason(), refund.getFailureReason());
    }
}
