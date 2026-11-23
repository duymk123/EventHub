package com.eventhub.order.entity;

import com.eventhub.order.common.OrderStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_order_expiry", columnList = "status, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    /** Order Service sinh UUID trước khi gọi Ticket reserve (orderId là khóa idempotency), không để Hibernate sinh. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "customer_id", nullable = false, length = 36)
    private UUID customerId;

    /** C02: snapshot email đã xác minh của người mua lúc tạo đơn, dùng cho confirm/ticket.issued/order.failed. */
    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, length = 36)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reservation_id", nullable = false, length = 36)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * C21: payment đã làm đơn chuyển PAID. Payment SUCCEEDED khác giá trị này (khách trả hai lần, hoặc tiền về khi đơn
     * đã hết hạn/hủy) nằm trong danh sách đối soát của ADMIN.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_id", length = 36)
    private UUID paymentId;

    /**
     * C04: giờ bắt đầu sự kiện lúc giữ ghế (Ticket trả về). Event không cho đổi giờ sau khi publish, nên dùng được
     * cho mốc 48 giờ hoàn tiền mà không phải gọi Event.
     */
    @Column(name = "event_start_time")
    private Instant eventStartTime;

    /** C06: Ticket đã phát vé cho đơn PAID; NULL khi PAID nghĩa là confirm còn phải thử lại. */
    @Column(name = "tickets_confirmed_at")
    private Instant ticketsConfirmedAt;

    /** C14: header Idempotency-Key của POST /orders, duy nhất theo (customer_id, idempotency_key). */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** C14: SHA-256 (hex) của nội dung request chuẩn hóa, để phát hiện dùng lại key với nội dung khác. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundRequest> refundRequests = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentWebhook> paymentWebhooks = new ArrayList<>();
}
