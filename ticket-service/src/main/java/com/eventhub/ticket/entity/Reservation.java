package com.eventhub.ticket.entity;

import com.eventhub.ticket.common.ReservationStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_reservation_expiry", columnList = "status, expires_at"))
@Getter @Setter @NoArgsConstructor
public class Reservation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 36)
    private UUID id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.RESERVED;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "released_at")
    private Instant releasedAt;
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationItem> items = new ArrayList<>();
}
