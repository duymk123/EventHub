package com.eventhub.order.entity;

import com.eventhub.order.common.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provider_transaction",
                        columnNames = {"provider", "provider_transaction_id"}),
                @UniqueConstraint(name = "uk_provider_txn_ref", columnNames = {"provider", "provider_txn_ref"})})
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    /** Gán trong code trước khi lưu vì provider_txn_ref (NOT NULL) suy ra từ id. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    private String provider;

    /** C06: vnp_TxnRef gửi VNPay (id payment bỏ gạch, 32 ký tự hex). */
    @Column(name = "provider_txn_ref", nullable = false, length = 40)
    private String providerTxnRef;

    /** vnp_TransactionNo do VNPay cấp khi thanh toán xong. */
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    /** vnp_PayDate (yyyyMMddHHmmss, giờ Việt Nam), cần khi gọi refund/querydr. */
    @Column(name = "provider_pay_date", length = 14)
    private String providerPayDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** C21: ADMIN đã xử lý khoản đối soát (hoàn tay trên cổng VNPay); khác NULL thì rời danh sách đối soát. */
    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reconciled_by", length = 36)
    private UUID reconciledBy;

    @Column(name = "reconciliation_note", length = 500)
    private String reconciliationNote;

}
