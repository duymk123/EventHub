package com.eventhub.order.service.impl;

import com.eventhub.order.client.TicketClient;
import com.eventhub.order.client.VnpayClient;
import com.eventhub.order.common.Actor;
import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.common.RefundStatus;
import com.eventhub.order.dto.response.RefundResponse;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.Payment;
import com.eventhub.order.entity.RefundRequest;
import com.eventhub.order.exception.BusinessException;
import com.eventhub.order.repository.OrderRepository;
import com.eventhub.order.repository.PaymentRepository;
import com.eventhub.order.repository.RefundRequestRepository;
import com.eventhub.order.service.OrderEvents;
import com.eventhub.order.service.OutboxWriter;
import com.eventhub.order.service.RefundAllocation;
import com.eventhub.order.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Luồng hoàn tiền C05 rút gọn. Mọi đổi trạng thái khóa đơn rồi khóa refund (cùng thứ tự với thanh toán/hết hạn);
 * gọi Ticket và VNPay ngoài transaction để không giữ lock DB khi gọi HTTP. Chỉ bên chuyển được refund sang
 * PROCESSING mới gọi VNPay, nên hai ADMIN bấm cùng lúc không hoàn hai lần.
 */
@Service
public class RefundServiceImpl implements RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);
    /** C04: phải còn ít nhất 48 giờ trước giờ bắt đầu sự kiện, lúc yêu cầu và lúc duyệt. */
    static final Duration REFUND_WINDOW = Duration.ofHours(48);
    /** Một đơn chỉ có một yêu cầu đang sống; bị từ chối thì khách được yêu cầu lại nếu còn hạn. */
    private static final Set<RefundStatus> BLOCKING = EnumSet.complementOf(EnumSet.of(RefundStatus.REJECTED));

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRequestRepository refunds;
    private final TicketClient ticketClient;
    private final VnpayClient vnpay;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RefundServiceImpl(OrderRepository orders, PaymentRepository payments, RefundRequestRepository refunds,
                             TicketClient ticketClient, VnpayClient vnpay, OutboxWriter outbox, TransactionTemplate tx,
                             Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.refunds = refunds;
        this.ticketClient = ticketClient;
        this.vnpay = vnpay;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- customer

    @Override
    public RefundResponse request(Actor customer, UUID orderId, String reason) {
        orders.findByIdAndCustomerId(orderId, customer.userId()).orElseThrow(RefundServiceImpl::orderNotFound);
        return tx.execute(status -> {
            Order order = orders.findByIdForUpdate(orderId).orElseThrow(RefundServiceImpl::orderNotFound);
            if (order.getStatus() != OrderStatus.PAID || order.getTicketsConfirmedAt() == null
                    || order.getPaymentId() == null) {
                throw new BusinessException(ErrorCode.ORDER_NOT_REFUNDABLE,
                        "Only paid orders with issued tickets can be refunded");
            }
            if (refunds.existsByOrderIdAndStatusIn(orderId, BLOCKING)) {
                throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS, "This order already has a refund request");
            }
            Instant now = clock.instant();
            if (!withinWindow(order, now)) {
                throw windowExpired();
            }
            RefundRequest refund = new RefundRequest();
            refund.setOrder(order);
            refund.setRequestedBy(customer.userId());
            refund.setAmount(RefundAllocation.total(order.getItems()));
            refund.setReason(reason.trim());
            refund.setStatus(RefundStatus.REQUESTED);
            refund.setRequestedAt(now);
            return RefundResponse.from(refunds.saveAndFlush(refund));
        });
    }

    @Override
    public List<RefundResponse> listForOrder(Actor customer, UUID orderId) {
        orders.findByIdAndCustomerId(orderId, customer.userId()).orElseThrow(RefundServiceImpl::orderNotFound);
        return refunds.findByOrderIdOrderByRequestedAtDesc(orderId).stream().map(RefundResponse::from).toList();
    }

    // ---------------------------------------------------------------- admin

    @Override
    public List<RefundResponse> listForAdmin(RefundStatus status) {
        List<RefundRequest> rows = status == null ? refunds.findAllByOrderByRequestedAtDesc()
                : refunds.findByStatusOrderByRequestedAtDesc(status);
        return rows.stream().map(RefundResponse::from).toList();
    }

    @Override
    public RefundResponse approve(Actor admin, UUID refundId, String clientIp) {
        UUID orderId = orderIdOf(refundId);
        RefundStatus next = tx.execute(status -> {
            Order order = orders.findByIdForUpdate(orderId).orElseThrow(RefundServiceImpl::orderNotFound);
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
            requireStatus(refund, RefundStatus.REQUESTED);
            Instant now = clock.instant();
            refund.setApprovedBy(admin.userId());
            refund.setReviewedAt(now);
            if (!withinWindow(order, now)) {
                // Quyết định 2026-11-23 (1a): duyệt muộn dưới 48 giờ thì tự từ chối, giữ đúng C04.
                refund.setStatus(RefundStatus.REJECTED);
                refund.setReviewReason("REFUND_WINDOW_EXPIRED: less than 48 hours before the event");
                return RefundStatus.REJECTED;
            }
            if (order.getStatus() != OrderStatus.PAID) {
                throw new BusinessException(ErrorCode.ORDER_NOT_REFUNDABLE, "Order is " + order.getStatus());
            }
            refund.setStatus(RefundStatus.PROCESSING);
            return RefundStatus.PROCESSING;
        });
        if (next == RefundStatus.PROCESSING) {
            execute(admin, refundId, orderId, clientIp);
        }
        return reload(refundId);
    }

    @Override
    public RefundResponse reject(Actor admin, UUID refundId, String reason) {
        UUID orderId = orderIdOf(refundId);
        tx.executeWithoutResult(status -> {
            orders.findByIdForUpdate(orderId).orElseThrow(RefundServiceImpl::orderNotFound);
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
            requireStatus(refund, RefundStatus.REQUESTED);
            refund.setStatus(RefundStatus.REJECTED);
            refund.setApprovedBy(admin.userId());
            refund.setReviewedAt(clock.instant());
            refund.setReviewReason(reason.trim());
        });
        return reload(refundId);
    }

    @Override
    public RefundResponse retry(Actor admin, UUID refundId, String clientIp) {
        UUID orderId = orderIdOf(refundId);
        tx.executeWithoutResult(status -> {
            orders.findByIdForUpdate(orderId).orElseThrow(RefundServiceImpl::orderNotFound);
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
            requireStatus(refund, RefundStatus.FAILED);
            // C05 rút gọn: vé có thể đã hủy ở lần trước nên không kiểm lại 48 giờ khi thử lại.
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setFailureReason(null);
        });
        execute(admin, refundId, orderId, clientIp);
        return reload(refundId);
    }

    // ---------------------------------------------------------------- execution

    /** Hủy vé trước, rồi mới gọi VNPay (C05 rút gọn). Mọi lỗi đưa refund về FAILED kèm mã lỗi. */
    private void execute(Actor admin, UUID refundId, UUID orderId, String clientIp) {
        try {
            ticketClient.cancelByOrder(orderId);
        } catch (BusinessException error) {
            fail(refundId, error.getErrorCode().name());
            return;
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        RefundCall call = tx.execute(status -> {
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
            refund.setProviderRequestId(requestId);
            Order order = refund.getOrder();
            Payment payment = payments.findById(order.getPaymentId())
                    .orElseThrow(() -> new IllegalStateException("Paid order " + orderId + " has no payment"));
            return new RefundCall(payment.getProviderTxnRef(), payment.getProviderTransactionId(),
                    VnpayClient.createDateOf(payment.getPaymentUrl()), refund.getAmount());
        });

        VnpayClient.RefundResult result;
        try {
            result = vnpay.refund(new VnpayClient.RefundCommand(requestId, call.txnRef(), call.transactionNo(),
                    call.transactionDate(), PaymentServiceImpl.toVnpAmount(call.amount()), admin.userId().toString(),
                    clientIp, "Hoan tien don hang " + orderId));
        } catch (BusinessException error) {
            fail(refundId, error.getErrorCode().name());
            return;
        }
        if (!"00".equals(result.responseCode())) {
            fail(refundId, "VNPAY_" + result.responseCode());
            return;
        }
        complete(refundId, orderId, result.transactionNo());
    }

    private record RefundCall(String txnRef, String transactionNo, String transactionDate, java.math.BigDecimal amount) {
    }

    private void complete(UUID refundId, UUID orderId, String providerRefundId) {
        tx.executeWithoutResult(status -> {
            Order order = orders.findByIdForUpdate(orderId).orElseThrow(RefundServiceImpl::orderNotFound);
            RefundRequest refund = refunds.findByIdForUpdate(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
            if (refund.getStatus() != RefundStatus.PROCESSING) {
                return;
            }
            Instant now = clock.instant();
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setRefundedAt(now);
            refund.setProviderRefundId(providerRefundId == null || providerRefundId.isBlank() ? null : providerRefundId);
            order.setStatus(OrderStatus.REFUNDED);
            outbox.append(OrderEvents.REFUNDED, order.getId(), order.getEventId(), now,
                    OrderEvents.refunded(order, refund, RefundAllocation.allocate(order.getItems())));
        });
    }

    private void fail(UUID refundId, String reason) {
        log.warn("Refund {} failed: {}", refundId, reason);
        tx.executeWithoutResult(status -> refunds.findByIdForUpdate(refundId).ifPresent(refund -> {
            if (refund.getStatus() == RefundStatus.PROCESSING) {
                refund.setStatus(RefundStatus.FAILED);
                refund.setFailureReason(reason);
            }
        }));
    }

    // ---------------------------------------------------------------- helpers

    private static boolean withinWindow(Order order, Instant now) {
        return order.getEventStartTime() != null && !now.isAfter(order.getEventStartTime().minus(REFUND_WINDOW));
    }

    private UUID orderIdOf(UUID refundId) {
        return refunds.findOrderIdById(refundId).orElseThrow(RefundServiceImpl::refundNotFound);
    }

    private RefundResponse reload(UUID refundId) {
        return refunds.findWithOrderById(refundId).map(RefundResponse::from)
                .orElseThrow(RefundServiceImpl::refundNotFound);
    }

    private static void requireStatus(RefundRequest refund, RefundStatus expected) {
        if (refund.getStatus() != expected) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATE,
                    "Refund is " + refund.getStatus() + ", expected " + expected);
        }
    }

    private static BusinessException windowExpired() {
        return new BusinessException(ErrorCode.REFUND_WINDOW_EXPIRED,
                "Refunds are only possible at least 48 hours before the event starts");
    }

    private static BusinessException orderNotFound() {
        return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
    }

    private static BusinessException refundNotFound() {
        return new BusinessException(ErrorCode.REFUND_NOT_FOUND, "Refund not found");
    }
}
