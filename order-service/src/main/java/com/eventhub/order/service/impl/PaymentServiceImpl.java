package com.eventhub.order.service.impl;

import com.eventhub.order.client.VnpayClient;
import com.eventhub.order.common.Actor;
import com.eventhub.order.common.ErrorCode;
import com.eventhub.order.common.OrderStatus;
import com.eventhub.order.common.PaymentStatus;
import com.eventhub.order.config.VnpayProperties;
import com.eventhub.order.dto.response.OrderResponse;
import com.eventhub.order.dto.response.PaymentResponse;
import com.eventhub.order.dto.response.ReconciliationItem;
import com.eventhub.order.entity.Order;
import com.eventhub.order.entity.Payment;
import com.eventhub.order.exception.BusinessException;
import com.eventhub.order.repository.OrderRepository;
import com.eventhub.order.repository.PaymentRepository;
import com.eventhub.order.service.OrderService;
import com.eventhub.order.service.PaymentResultService;
import com.eventhub.order.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    /** Còn dưới chừng này thì khách không kịp thanh toán, báo ORDER_EXPIRED luôn. */
    private static final long MIN_PAY_WINDOW_SECONDS = 20;
    private static final int MAX_KEY_LENGTH = 100;
    private static final int RECONCILIATION_LIMIT = 100;

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderService orderService;
    private final PaymentResultService results;
    private final VnpayClient vnpay;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentServiceImpl(OrderRepository orders, PaymentRepository payments, OrderService orderService,
                              PaymentResultService results, VnpayClient vnpay, TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.orderService = orderService;
        this.results = results;
        this.vnpay = vnpay;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public PaymentResponse initiate(Actor customer, UUID orderId, String idempotencyKey, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key header is required (max " + MAX_KEY_LENGTH + " characters)");
        }
        Order order = orders.findByIdAndCustomerId(orderId, customer.userId())
                .orElseThrow(PaymentServiceImpl::orderNotFound);
        PaymentResponse existing = existingPayment(idempotencyKey, order);
        if (existing != null) {
            return existing;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PAYABLE, "Order is " + order.getStatus());
        }
        Instant now = clock.instant();
        if (!order.getExpiresAt().isAfter(now.plusSeconds(MIN_PAY_WINDOW_SECONDS))) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED, "Payment time for this order is over");
        }
        if (!vnpay.isConfigured()) {
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "VNPay is not configured");
        }

        UUID paymentId = UUID.randomUUID();
        String txnRef = paymentId.toString().replace("-", "");
        String paymentUrl = vnpay.buildPaymentUrl(payParams(order, txnRef, clientIp, now));
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setProvider(VnpayClient.PROVIDER);
        payment.setProviderTxnRef(txnRef);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentUrl(paymentUrl);
        payment.setIdempotencyKey(idempotencyKey);
        try {
            tx.executeWithoutResult(status -> {
                payment.setOrder(orders.getReferenceById(orderId));
                payments.save(payment);
            });
        } catch (DataIntegrityViolationException error) {
            // Hai request cùng key chạy song song: bản thua đọc lại payment của bản thắng.
            PaymentResponse winner = existingPayment(idempotencyKey, order);
            if (winner == null) {
                throw error;
            }
            return winner;
        }
        return PaymentResponse.from(payment, orderId, order.getExpiresAt());
    }

    /**
     * Cùng key cho cùng đơn trả lại payment cũ; key đã dùng cho đơn khác là 409. Thanh toán lại sau khi thất bại
     * (C18) dùng key mới nên tạo payment mới với vnp_TxnRef mới.
     */
    private PaymentResponse existingPayment(String idempotencyKey, Order order) {
        return payments.findWithOrderByIdempotencyKey(idempotencyKey).map(payment -> {
            if (!payment.getOrder().getId().equals(order.getId())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used for another request");
            }
            return PaymentResponse.from(payment, order.getId(), order.getExpiresAt());
        }).orElse(null);
    }

    private Map<String, String> payParams(Order order, String txnRef, String clientIp, Instant now) {
        VnpayProperties config = vnpay.properties();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", config.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", config.getTmnCode());
        params.put("vnp_Amount", String.valueOf(toVnpAmount(order.getTotalAmount())));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        // Không dấu, không ký tự đặc biệt theo khuyến nghị của VNPay.
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", config.getLocale());
        params.put("vnp_ReturnUrl", config.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", VnpayClient.formatTime(now));
        params.put("vnp_ExpireDate", VnpayClient.formatTime(order.getExpiresAt()));
        return params;
    }

    @Override
    public OrderResponse verify(Actor customer, UUID orderId, String clientIp) {
        orders.findByIdAndCustomerId(orderId, customer.userId()).orElseThrow(PaymentServiceImpl::orderNotFound);
        var pending = payments.findByOrderIdAndStatusOrderByCreatedAtAsc(orderId, PaymentStatus.PENDING);
        if (!pending.isEmpty() && !vnpay.isConfigured()) {
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "VNPay is not configured");
        }
        for (Payment payment : pending) {
            reconcile(payment, clientIp);
        }
        return orderService.getDetail(customer, orderId);
    }

    /**
     * querydr: 00 + TransactionStatus 00 là thành công, 02 là thất bại; 01 (chưa hoàn tất) hoặc mã 91 (khách chưa
     * thanh toán) giữ PENDING để job hết hạn xử lý. Mã lỗi khác của VNPay là lỗi tạm thời.
     */
    private void reconcile(Payment payment, String clientIp) {
        String createDate = VnpayClient.createDateOf(payment.getPaymentUrl());
        VnpayClient.QueryResult result = vnpay.queryTransaction(payment.getProviderTxnRef(), createDate, clientIp);
        if ("91".equals(result.responseCode())) {
            return;
        }
        if (!"00".equals(result.responseCode())) {
            log.warn("VNPay querydr for payment {} returned {}", payment.getId(), result.responseCode());
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "VNPay is unavailable, please retry");
        }
        if (!String.valueOf(toVnpAmount(payment.getAmount())).equals(result.amount())) {
            log.warn("VNPay querydr amount mismatch for payment {}", payment.getId());
            return;
        }
        boolean success = "00".equals(result.transactionStatus());
        if (success || "02".equals(result.transactionStatus())) {
            results.apply(payment.getId(), new PaymentResultService.ProviderResult(success,
                    result.transactionStatus(), blankToNull(result.transactionNo()), blankToNull(result.payDate())),
                    null);
        }
    }

    @Override
    public List<ReconciliationItem> reconciliation() {
        return payments.findNeedingReconciliation(PageRequest.of(0, RECONCILIATION_LIMIT)).stream()
                .map(ReconciliationItem::from).toList();
    }

    /**
     * C21: ADMIN đánh dấu đã xử lý (đã hoàn tay trên cổng VNPay). Chỉ payment đang nằm trong danh sách đối soát;
     * gọi lại khi đã xử lý trả trạng thái hiện tại.
     */
    @Override
    public ReconciliationItem resolve(Actor admin, UUID paymentId, String note) {
        UUID orderId = payments.findOrderIdById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        return tx.execute(status -> {
            Order order = orders.findByIdForUpdate(orderId).orElseThrow(PaymentServiceImpl::orderNotFound);
            Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(
                    () -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
            boolean reconcilable = payment.getStatus() == PaymentStatus.SUCCEEDED
                    && (order.getPaymentId() == null || !order.getPaymentId().equals(paymentId));
            if (!reconcilable) {
                throw new BusinessException(ErrorCode.PAYMENT_NOT_RECONCILABLE,
                        "Payment is not waiting for reconciliation");
            }
            if (payment.getReconciledAt() == null) {
                payment.setReconciledAt(clock.instant());
                payment.setReconciledBy(admin.userId());
                payment.setReconciliationNote(note.trim());
            }
            return ReconciliationItem.from(payment);
        });
    }

    /** VNPay nhận số tiền × 100, không phần thập phân. */
    static long toVnpAmount(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BusinessException orderNotFound() {
        return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
    }
}
