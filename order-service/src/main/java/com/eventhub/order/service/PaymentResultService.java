package com.eventhub.order.service;

import com.eventhub.order.client.TicketClient;
import com.eventhub.order.client.VnpayClient;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.common.PaymentStatus;
import com.eventhub.order.common.WebhookStatus;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.Payment;
import com.eventhub.order.entity.PaymentWebhook;
import com.eventhub.order.repository.OrderRepository;
import com.eventhub.order.repository.PaymentRepository;
import com.eventhub.order.repository.PaymentWebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Áp kết quả cuối cùng của VNPay cho một payment PENDING (C06). IPN và querydr dùng chung hàm này nên xử lý lặp
 * không đổi trạng thái hai lần. Thanh toán thất bại/khách hủy trên VNPay chỉ đánh dấu payment FAILED, đơn vẫn
 * PENDING_PAYMENT để khách thanh toán lại đến hết hạn giữ ghế (C18). Trong transaction: khóa đơn rồi khóa payment (cùng thứ tự với expiry), đổi trạng thái,
 * ghi outbox. Sau commit mới gọi Ticket (không giữ lock DB khi gọi HTTP).
 */
@Service
public class PaymentResultService {
    private static final Logger log = LoggerFactory.getLogger(PaymentResultService.class);

    /** Kết quả VNPay: success khi vnp_ResponseCode = 00 và vnp_TransactionStatus = 00. */
    public record ProviderResult(boolean success, String responseCode, String transactionNo, String payDate) {
    }

    /** Bản ghi IPN cho payment_webhooks; null khi kết quả đến từ querydr. */
    public record WebhookRecord(String providerEventId, String payloadHash) {
    }

    public enum Outcome { APPLIED, ALREADY_PROCESSED }

    private enum FollowUp { NONE, CONFIRM_TICKETS, RELEASE_SEATS }

    private record Applied(Outcome outcome, FollowUp followUp, UUID orderId, UUID reservationId) {
    }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentWebhookRepository webhooks;
    private final OutboxWriter outbox;
    private final TicketConfirmationService confirmation;
    private final TicketClient ticketClient;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentResultService(OrderRepository orders, PaymentRepository payments, PaymentWebhookRepository webhooks,
                                OutboxWriter outbox, TicketConfirmationService confirmation, TicketClient ticketClient,
                                TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.webhooks = webhooks;
        this.outbox = outbox;
        this.confirmation = confirmation;
        this.ticketClient = ticketClient;
        this.tx = tx;
        this.clock = clock;
    }

    public Outcome apply(UUID paymentId, ProviderResult result, WebhookRecord webhook) {
        Applied applied = tx.execute(status -> applyInTransaction(paymentId, result, webhook));
        switch (applied.followUp()) {
            case CONFIRM_TICKETS -> confirmation.confirmQuietly(applied.orderId());
            case RELEASE_SEATS -> ticketClient.releaseQuietly(applied.reservationId());
            case NONE -> {
            }
        }
        return applied.outcome();
    }

    private Applied applyInTransaction(UUID paymentId, ProviderResult result, WebhookRecord webhook) {
        UUID orderId = payments.findOrderIdById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " not found"));
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " not found"));
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " not found"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return new Applied(Outcome.ALREADY_PROCESSED, FollowUp.NONE, orderId, order.getReservationId());
        }
        Instant now = clock.instant();
        if (webhook != null) {
            recordWebhook(order, webhook, now);
        }
        return result.success() ? succeed(order, payment, result, now) : fail(order, payment, result);
    }

    private Applied succeed(Order order, Payment payment, ProviderResult result, Instant now) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderTransactionId(result.transactionNo());
        payment.setProviderPayDate(result.payDate());
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getExpiresAt().isAfter(now)) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(now);
            order.setPaymentId(payment.getId());
            return new Applied(Outcome.APPLIED, FollowUp.CONFIRM_TICKETS, order.getId(), order.getReservationId());
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            // Tiền về đúng lúc đơn vừa quá hạn nhưng job expiry chưa chạy: Ticket cũng đã hết hạn giữ ghế.
            order.setStatus(OrderStatus.EXPIRED);
            outbox.append(OrderEvents.FAILED, order.getId(), order.getEventId(), now,
                    OrderEvents.failed(order, "EXPIRED"));
            log.warn("Payment {} succeeded after order {} expired; needs manual reconciliation", payment.getId(),
                    order.getId());
            return new Applied(Outcome.APPLIED, FollowUp.RELEASE_SEATS, order.getId(), order.getReservationId());
        }
        // Đơn đã EXPIRED/CANCELLED (hoặc PAID bởi payment khác): giữ nguyên đơn, ADMIN đối soát thủ công (C21).
        log.warn("Payment {} succeeded but order {} is {}; needs manual reconciliation", payment.getId(),
                order.getId(), order.getStatus());
        return new Applied(Outcome.APPLIED, FollowUp.NONE, order.getId(), order.getReservationId());
    }

    /** C18: chỉ payment thất bại; đơn và ghế giữ nguyên, job hết hạn xử lý nếu khách không thanh toán lại. */
    private Applied fail(Order order, Payment payment, ProviderResult result) {
        payment.setStatus(PaymentStatus.FAILED);
        log.info("Payment {} failed with VNPay code {}; order {} stays {}", payment.getId(), result.responseCode(),
                order.getId(), order.getStatus());
        return new Applied(Outcome.APPLIED, FollowUp.NONE, order.getId(), order.getReservationId());
    }

    private void recordWebhook(Order order, WebhookRecord webhook, Instant now) {
        PaymentWebhook row = new PaymentWebhook();
        row.setProvider(VnpayClient.PROVIDER);
        row.setProviderEventId(webhook.providerEventId());
        row.setOrder(order);
        row.setPayloadHash(webhook.payloadHash());
        row.setReceivedAt(now);
        row.setProcessedAt(now);
        row.setStatus(WebhookStatus.PROCESSED);
        webhooks.save(row);
    }
}
