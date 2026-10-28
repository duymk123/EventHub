package com.eventhub.order.entity;

import com.eventhub.order.common.WebhookStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhooks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_event",
                columnNames = {"provider", "provider_event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "payload_hash", nullable = false, length = 255)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookStatus status = WebhookStatus.RECEIVED;
}
